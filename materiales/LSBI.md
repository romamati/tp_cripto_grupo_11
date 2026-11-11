# Contrato de LSBI y referencias oficiales

Esta especificación describe la implementación de la fase 2 en
`src/stegobmp/steg/Lsbi.java`. Los resultados de referencia de este documento se
verificaron mediante una comprobación independiente sobre los archivos de la cátedra
y se utilizan en las pruebas de capacidad, extracción y ocultamiento.

## Fuentes y decisiones

- [Consigna](consigna.pdf), secciones 5.3 y 5.4: formato del mensaje, orden físico
  BGR, cuatro indicadores previos al secreto, tamaños en big endian y uso del
  cuerpo completo, incluido el padding de las filas.
- [Paper de Majeed y Sulaiman](paper_lsbi.pdf), secciones 5 y 6: uso de los canales
  azul y verde, clasificación por los bits adyacentes al LSB e inversión según la
  cantidad de cambios.
- [Ejemplo LSBI sin cifrar](ejemplo/ladoLSBI.bmp), su
  [portador original](ejemplo/lado.bmp) y el
  [README de la cátedra](ejemplo/README_ejemplos.txt): referencias para comprobar el
  recorrido, el payload y la reproducción exacta del BMP.

El TP fija detalles que el paper deja abiertos, como la ubicación y el orden de
los indicadores. Para esos detalles se sigue la consigna. Las decisiones que el
ejemplo no alcanza a validar se explicitan a continuación para probarlas durante
la implementación.

## Cuerpo del portador y recorrido

`body` es el arreglo recibido por `StegMethod`: todos los bytes posteriores al
encabezado, incluido el padding. Los índices de este documento empiezan en cero y
son relativos a ese arreglo, no al archivo completo.

Los primeros cuatro bytes se reservan para los indicadores. El mensaje se recorre
desde `i = 4` en orden creciente, usando los índices que cumplen `i % 3 != 2`.
La cuenta de los canales empieza en el inicio del cuerpo y **no se reinicia**
después de los indicadores.

| Índice en `body` | Canal del ciclo BGR | Uso |
| --- | --- | --- |
| 0 | Azul | Indicador del patrón `00` |
| 1 | Verde | Indicador del patrón `01` |
| 2 | Rojo | Indicador del patrón `10` |
| 3 | Azul | Indicador del patrón `11` |
| 4 | Verde | Primer bit del mensaje |
| 5 | Rojo | Se omite |
| 6 | Azul | Segundo bit del mensaje |
| 7 | Verde | Tercer bit del mensaje |
| 8 | Rojo | Se omite |
| 9 | Azul | Cuarto bit del mensaje |

Los primeros ocho bits del mensaje ocupan los índices
`4, 6, 7, 9, 10, 12, 13, 15`. Cada byte del mensaje se procesa desde su bit más
significativo hasta el menos significativo.

Para el padding se adopta un recorrido lineal del cuerpo: no se eliminan sus
bytes ni se reinicia el ciclo al cambiar de fila. Así, `i % 3` define qué bytes se
utilizan también en esa región. Esta convención es compatible con el contrato que
recibe únicamente `body` y con la indicación de usar el bloque completo. El ejemplo
`lado.bmp`, de 640 píxeles de ancho, no tiene padding de filas; por lo tanto, su
comparación no valida ese caso. Las pruebas por CLI lo cubren con portadores
adicionales que tienen 1, 2 y 3 bytes de padding por fila.

## Indicadores y patrones

El patrón de un byte es `(b >> 1) & 0b11`: sus bits 2 y 1, tomando el LSB como bit
0. Como solo se modifica el bit 0, el patrón permanece estable durante el
ocultamiento.

Los indicadores se guardan en el LSB de los cuatro bytes reservados, en el orden
`00`, `01`, `10`, `11`. Un `1` significa que se invirtió ese patrón; un `0`, que no
se invirtió. Estos cuatro bits usan LSB1 normal, incluyen el byte rojo del índice
2 y no participan de los conteos ni de las inversiones del mensaje.

Por ejemplo, si se invierten `00` y `10`, los indicadores son `1010`.

## Ocultamiento e inversión

`data` es el payload completo preparado por el programa, no solo el contenido del
archivo. Para ocultarlo:

1. Comprobar que haya al menos cuatro bytes para los indicadores y que
   `data.length <= capacity(body.length)`, antes de modificar el cuerpo.
2. Para cada bit del payload, tomar el siguiente índice elegible y comparar el
   bit con el LSB original. Acumular, por patrón, cuántos LSB cambiarían y cuántos
   quedarían iguales con LSB1 normal.
3. Marcar un patrón para inversión únicamente si `cambiados > sinCambios`.
   Un empate o un patrón sin ocurrencias deja su indicador en `0`.
4. Escribir cada bit del payload, invertido si su patrón está marcado. Solo
   cambian los LSB de los bytes efectivamente utilizados.
5. Guardar los cuatro indicadores en los bytes reservados.

Se cuenta respecto del portador original y únicamente sobre los bytes que alojan
el payload. No se incluyen bytes rojos omitidos, indicadores ni bytes posteriores
al último bit del payload. La inversión tampoco se extiende a esas regiones.

El paper contiene ambigüedades en su ejemplo sobre patrones con una sola
ocurrencia. Se adopta la regla general de mayoría estricta también para ellos:
si su único LSB cambiaría, se marca la inversión; si no cambiaría, no se marca.
Ese caso y los empates se cubren con pruebas propias, porque el ejemplo oficial
no los ejercita.

Se preservan el encabezado completo, los siete bits superiores de cada byte y
todos los bytes no utilizados. El byte rojo del índice 2 es la única excepción al
salto del rojo, porque contiene un indicador.

## Capacidad y extracción

La capacidad se expresa en **bytes del payload completo**. Para una longitud no
negativa `n` del cuerpo:

```text
si n < 4:
    capacidad = 0
si n >= 4:
    bytes_elegibles = n - floor(n / 3) - 3
    capacidad = floor(bytes_elegibles / 8)
```

`floor(n / 3)` cuenta los bytes rojos del cuerpo. Se descuentan otros tres bytes
porque los indicadores ocupan tres posiciones que normalmente serían elegibles;
el cuarto indicador ocupa un rojo que ya fue descontado. Los bits sobrantes no
forman otro byte recuperable. La capacidad disponible para el contenido del archivo
es menor, porque el payload incluye su tamaño, extensión y, si corresponde, la
sobrecarga del cifrado.

`extractAll` lee los indicadores, recorre los mismos índices que `embed` y recupera
cada bit como `(body[i] & 1) ^ indicador[patrón]`. Devuelve todos los bytes completos
recuperables sin modificar `body`. Si hay menos de cuatro bytes de cuerpo, devuelve
un arreglo vacío, coherente con capacidad cero.

La extracción no termina por un byte nulo ni interpreta el tamaño. Los bytes
posteriores al mensaje pueden contener datos arbitrarios; `Extractor` y
`PayloadFormat` delimitan el bloque real y su extensión.

El formato del payload se conserva:

- Sin cifrado: `tamaño real (4 bytes, big endian) || archivo || extensión con \0`.
- Con cifrado: `tamaño cifrado (4 bytes, big endian) || cifrado del payload anterior`.

Los cuatro indicadores son adicionales a ese formato. LSBI no deriva claves,
cifra ni descifra; recibe y devuelve bytes independientemente de su contenido.

## Vector oficial sin cifrado

La comprobación independiente del 10 de noviembre de 2026 produjo estos valores:

| Dato | Valor esperado |
| --- | --- |
| Portador | `materiales/ejemplo/lado.bmp` |
| Referencia | `materiales/ejemplo/ladoLSBI.bmp` |
| Dimensiones | 640 × 480 píxeles, 24 bits por píxel |
| Inicio del cuerpo | Offset 54 del archivo |
| Longitud del archivo BMP | 921.654 bytes |
| Longitud del cuerpo | 921.600 bytes |
| Bytes elegibles después de los indicadores | 614.397 |
| Capacidad LSBI | 76.799 bytes de payload; sobran 5 bits |
| Indicadores | `1111` |
| Prefijo de tamaño recuperado, en hexadecimal | `00 00 AF 56` |
| Tamaño del PNG | 44.886 bytes |
| Extensión recuperada | `.png\0`, 5 bytes |
| Longitud del payload | 44.895 bytes: `4 + 44.886 + 5` |
| Último índice de `body` utilizado para el payload | 538.743 |
| SHA-256 del PNG recuperado | `b93be15382f35f099ca286241dd7bb6e681d5bce8d315ac0b6240f0b516f549d` |
| SHA-256 de `lado.bmp` | `fee3bdabe02a5d7a178955864106a923dace797bc45a642c5bfd54127b742884` |
| SHA-256 de `ladoLSBI.bmp` | `701b0eb568f5c864c203d3baf6d4162d3b00002e9f7af4a65652cda498e177c2` |

Los conteos previos a la inversión, incluyendo tamaño y extensión, son:

| Patrón | Sin cambios | Cambiados | Indicador |
| --- | --- | --- | --- |
| `00` | 32.028 | 32.367 | 1 |
| `01` | 32.263 | 32.650 | 1 |
| `10` | 34.980 | 35.119 | 1 |
| `11` | 71.581 | 88.172 | 1 |

Los conteos suman 359.160 bits, equivalentes a los 44.895 bytes del payload. La
reconstrucción a partir del portador original coincidió con la referencia byte a
byte, incluidos el encabezado y el resto del cuerpo no utilizado.

Reiniciar el ciclo BGR después de los indicadores produce un tamaño de 109.078
bytes, incompatible con la capacidad del portador y con el PNG esperado. Esta
comprobación descarta ese recorrido para los ejemplos de la cátedra.

## Validación de la fase 2

Desde la raíz del repositorio:

```sh
make test-cli
make comparar
```

El primer comando ejecuta las pruebas unitarias y los 23 casos por línea de
comandos sin cifrado. El segundo reproduce la comparación entre métodos en
memoria; las métricas y su interpretación están en el
[informe de la fase 2](../informe/Fase2-LSBI.md).

Para verificar manualmente la extracción y reproducción de LSBI:

```sh
(
    set -eu
    DIRECTORIO_PRUEBA="$(mktemp -d)"
    trap 'rm -rf -- "$DIRECTORIO_PRUEBA"' EXIT
    ./stegobmp -extract -p materiales/ejemplo/ladoLSBI.bmp -out "$DIRECTORIO_PRUEBA/recuperado" -steg LSBI
    wc -c < "$DIRECTORIO_PRUEBA/recuperado.png"
    sha256sum "$DIRECTORIO_PRUEBA/recuperado.png"
    ./stegobmp -embed -in "$DIRECTORIO_PRUEBA/recuperado.png" -p materiales/ejemplo/lado.bmp -out "$DIRECTORIO_PRUEBA/regenerado.bmp" -steg LSBI
    cmp materiales/ejemplo/ladoLSBI.bmp "$DIRECTORIO_PRUEBA/regenerado.bmp"
)
```

El resultado esperado es un PNG de 44886 bytes con el hash de la tabla y un
`cmp` sin diferencias. Los casos con cifrado se comprobarán integralmente al
integrar la fase 3.
