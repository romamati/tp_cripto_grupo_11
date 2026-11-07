#!/bin/bash
set -u

RAIZ="$(cd "$(dirname "$0")" && pwd)"
EJEMPLOS="$RAIZ/materiales/ejemplo"
PORTADOR="$EJEMPLOS/lado.bmp"
PASSWORD=margarita
FIRMA_PNG=89504e470d0a1a0a
CIERRE_PNG=49454e44ae426082

if [ ! -f "$PORTADOR" ]; then
    echo "Error: no se encuentran los ejemplos de la cátedra en $EJEMPLOS" >&2
    exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
trap 'exit 130' INT TERM
cd "$TMP" || exit 1

STEGOBMP="$TMP/build/stegobmp"
ERRORES="$TMP/stderr"
pasaron=0
fallaron=()

seccion() { echo; echo "== $1"; }
ok() { echo "  OK    $1"; pasaron=$((pasaron + 1)); }
falla() { echo "  FALLA $1: $2"; fallaron+=("$1"); }

correr() { "$STEGOBMP" "$@" >/dev/null 2>"$ERRORES"; }
motivo() { grep -m 1 '^Error' "$ERRORES" || head -n 1 "$ERRORES" | grep . || echo "terminó con error y sin mensaje"; }

hex() { od -An -tx1 | tr -d ' \n'; }
es_png() { [ -f "$1" ] && [ "$(head -c 8 "$1" | hex)" = "$FIRMA_PNG" ] && [ "$(tail -c 8 "$1" | hex)" = "$CIERRE_PNG" ]; }
sha256() { sha256sum "$1" | cut -d ' ' -f 1; }

diferencia_de_bytes() {
    local generado=$1 esperado=$2
    if [ "$(wc -c <"$generado")" -ne "$(wc -c <"$esperado")" ]; then
        echo "los tamaños son distintos"
        return
    fi
    echo "difieren $(cmp -l "$generado" "$esperado" | wc -l) bytes; el primero está en la posición" \
        "$(cmp -l "$generado" "$esperado" | head -n 1 | awk '{print $1}') (contando desde 1)"
}

compilar() {
    seccion "Compilación (copia del código en $TMP)"
    mkdir "$TMP/build"
    cp -R "$RAIZ/src" "$RAIZ/Makefile" "$RAIZ/stegobmp" "$TMP/build/"
    local salida
    if ! salida=$(make -s -C "$TMP/build" 2>&1); then
        echo "$salida"
        echo "  FALLA make: no compila"
        exit 1
    fi
    [ -n "$salida" ] && echo "$salida" | sed 's/^/        /'
    ok "make"
}

extraer_ejemplo() {
    local archivo=$1
    shift
    local salida="$TMP/${archivo%.bmp}"
    local caso="extraer $archivo ($*)"
    if ! correr -extract -p "$EJEMPLOS/$archivo" -out "$salida" "$@"; then
        falla "$caso" "$(motivo)"
    elif ! es_png "$salida.png"; then
        falla "$caso" "no salió un .png válido (se generó: $(cd "$TMP" && ls "${archivo%.bmp}".* 2>/dev/null | tr '\n' ' '))"
    else
        ok "$caso"
    fi
}

sin_nada_oculto() {
    local metodo=$1
    local caso="extraer lado.bmp (-steg $metodo), que no tiene nada oculto"
    if correr -extract -p "$PORTADOR" -out "$TMP/lado_$metodo" -steg "$metodo"; then
        falla "$caso" "extrajo algo de un portador sin información oculta"
    elif grep -q "no se encontró información oculta" "$ERRORES"; then
        ok "$caso"
    else
        falla "$caso" "$(motivo)"
    fi
}

volver_a_ocultar() {
    local archivo=$1
    shift
    local png="$TMP/${archivo%.bmp}.png"
    local generado="$TMP/${archivo%.bmp}_otra_vez.bmp"
    local caso="ocultar de nuevo el .png de $archivo ($*) y comparar"
    if ! es_png "$png"; then
        falla "$caso" "no hay .png extraído para ocultar"
    elif ! correr -embed -in "$png" -p "$PORTADOR" -out "$generado" "$@"; then
        falla "$caso" "$(motivo)"
    elif cmp -s "$generado" "$EJEMPLOS/$archivo"; then
        ok "$caso"
    else
        falla "$caso" "$(diferencia_de_bytes "$generado" "$EJEMPLOS/$archivo")"
    fi
}

ida_y_vuelta() {
    local nombre=$1
    shift
    local base="$TMP/ida_y_vuelta_${nombre// /_}"
    local caso="ida y vuelta $nombre"
    if ! correr -embed -in "$PRUEBA" -p "$PORTADOR" -out "$base.bmp" "$@"; then
        falla "$caso" "al ocultar: $(motivo)"
    elif ! correr -extract -p "$base.bmp" -out "$base" "$@"; then
        falla "$caso" "al extraer: $(motivo)"
    elif [ ! -f "$base.txt" ]; then
        falla "$caso" "no se generó el .txt"
    elif [ "$(sha256 "$PRUEBA")" != "$(sha256 "$base.txt")" ]; then
        falla "$caso" "el sha256 del archivo extraído no coincide"
    else
        ok "$caso"
    fi
}

crear_archivo_de_prueba() {
    PRUEBA="$TMP/prueba.txt"
    for linea in $(seq 1 100); do
        echo "Línea $linea de la prueba de stegobmp: ñandú, acción, pingüino."
    done >"$PRUEBA"
    printf "$(printf '\\%03o' $(seq 0 255))" >>"$PRUEBA"
}

EJEMPLOS_CATEDRA=(
    "ladoLSB1.bmp -steg LSB1"
    "ladoLSB4.bmp -steg LSB4"
    "ladoLSBI.bmp -steg LSBI"
    "ladoLSB1aes128cbc.bmp -steg LSB1 -a aes128 -m cbc -pass $PASSWORD"
    "ladoLSBIaes256ofb.bmp -steg LSBI -a aes256 -m ofb -pass $PASSWORD"
    "ladoLSBIdescfb.bmp -steg LSBI -a 3des -m cfb -pass $PASSWORD"
)

echo "Java: $(java -version 2>&1 | head -n 1)"
compilar

seccion "Extracción de los ejemplos de la cátedra"
for ejemplo in "${EJEMPLOS_CATEDRA[@]}"; do
    extraer_ejemplo $ejemplo
done
for metodo in LSB1 LSB4 LSBI; do
    sin_nada_oculto $metodo
done

seccion "Volver a ocultar cada .png en lado.bmp y comparar con cmp"
for ejemplo in "${EJEMPLOS_CATEDRA[@]}"; do
    volver_a_ocultar $ejemplo
done

seccion "Ida y vuelta con un archivo de prueba en lado.bmp"
crear_archivo_de_prueba
for metodo in LSB1 LSB4 LSBI; do
    ida_y_vuelta "$metodo sin cifrar" -steg $metodo
    for algoritmo in aes128 aes192 aes256 3des; do
        for modo in ecb cfb ofb cbc; do
            ida_y_vuelta "$metodo $algoritmo $modo" -steg $metodo -a $algoritmo -m $modo -pass "$PASSWORD"
        done
    done
done

total=$((pasaron + ${#fallaron[@]}))
echo
echo "Resultado: pasaron $pasaron de $total casos."
if [ ${#fallaron[@]} -gt 0 ]; then
    echo "Fallaron ${#fallaron[@]}:"
    printf '  - %s\n' "${fallaron[@]}"
    exit 1
fi
