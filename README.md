# stegobmp — Grupo 11

Trabajo práctico de implementación de Criptografía y Seguridad (72.04, ITBA).

`stegobmp` oculta un archivo cualquiera dentro de una imagen BMP de 24 bits sin compresión y recupera
lo ocultado. LSB1, LSB4 y LSBI permiten ocultar y extraer sin cifrado. El cifrado con password
corresponde a la fase 3 y todavía no está implementado.

## Integrantes

- Matías Romanato
- Román Berruti
- Agustín Alonso
- Agustín Brunero

## Requisitos

- Linux. La consigna utiliza WSL2 con Ubuntu 22.04.3 LTS y exige funcionamiento en pampero.
  La fase 2 se verificó localmente en WSL2 con Ubuntu 24.04 y Java 21; la prueba en pampero queda pendiente.
- JDK 21, que incluye `java`, `javac` y `jar`. No se usan librerías externas: el cifrado usa la JCE del JDK.
- `make`.

En Ubuntu se instalan con:

```sh
sudo apt update
sudo apt install -y openjdk-21-jdk-headless make
```

## Compilación

Desde la carpeta del proyecto:

```sh
make
```

Compila las fuentes de `src/` con `javac`, genera `stegobmp.jar` y deja ejecutable el script `stegobmp`.

Para borrar todo lo generado:

```sh
make clean
```

## Ejecución

El script `stegobmp` de la raíz llama a `java -jar stegobmp.jar` con los mismos argumentos:

```sh
./stegobmp -embed -in mensaje1.txt -p imagen1.bmp -out imagenmas1.bmp -steg LSBI
```

Para usarlo sin `./` desde cualquier carpeta, se agrega la carpeta del proyecto al `PATH`:

```sh
export PATH="$PWD:$PATH"
stegobmp -extract -p imagenmas1.bmp -out mensaje1 -steg LSBI
```

### Ocultar

```
stegobmp -embed -in <archivo> -p <portador.bmp> -out <salida.bmp> -steg <LSB1|LSB4|LSBI>
         [-a <aes128|aes192|aes256|3des>] [-m <ecb|cfb|ofb|cbc>] [-pass <password>]
```

### Extraer

```
stegobmp -extract -p <portador.bmp> -out <archivo_sin_extension> -steg <LSB1|LSB4|LSBI>
         [-a <aes128|aes192|aes256|3des>] [-m <ecb|cfb|ofb|cbc>] [-pass <password>]
```

El archivo recuperado se guarda como `<archivo_sin_extension>` seguido de la extensión que se había
ocultado. Por ejemplo, con `-out mensaje1` se obtiene `mensaje1.txt`.

### Parámetros

| Parámetro   | Valores                              | Descripción                                          |
|-------------|--------------------------------------|------------------------------------------------------|
| `-embed`    |                                      | Oculta un archivo.                                   |
| `-extract`  |                                      | Extrae un archivo oculto.                            |
| `-in`       | ruta                                 | Archivo a ocultar (solo con `-embed`).               |
| `-p`        | ruta                                 | Imagen BMP portadora.                                |
| `-out`      | ruta                                 | BMP de salida (`-embed`) o archivo sin extensión (`-extract`). |
| `-steg`     | `LSB1`, `LSB4`, `LSBI`               | Método de esteganografía.                            |
| `-a`        | `aes128`, `aes192`, `aes256`, `3des` | Algoritmo de cifrado.                                |
| `-m`        | `ecb`, `cfb`, `ofb`, `cbc`           | Modo de cifrado.                                     |
| `-pass`     | texto                                | Password de cifrado.                                 |

- Los parámetros pueden ir en cualquier orden y los nombres y valores distinguen mayúsculas de minúsculas.
- Sin `-pass` no se cifra. Si se indican `-a` o `-m` sin `-pass`, se ignoran con un aviso.
- Con `-pass` y sin `-m` se usa `cbc`; con `-pass` y sin `-a` se usa `aes128`.
- También se acepta el guion largo (`–embed`), por si los parámetros se copian del enunciado.

### Ejemplos

Ocultar y recuperar un archivo con LSBI, sin cifrado:

```sh
./stegobmp -embed -in mensaje1.txt -p imagen1.bmp -out imagenmas1.bmp -steg LSBI
./stegobmp -extract -p imagenmas1.bmp -out mensaje1 -steg LSBI
```

Extraer los ejemplos sin cifrado de la cátedra:

```sh
./stegobmp -extract -p materiales/ejemplo/ladoLSB1.bmp -out lsb1 -steg LSB1
./stegobmp -extract -p materiales/ejemplo/ladoLSBI.bmp -out lsbi -steg LSBI
```

La sintaxis prevista para cifrado en la fase 3 es:

```sh
./stegobmp -embed -in mensaje1.txt -p imagen1.bmp -out imagenmas1.bmp -steg LSBI -a 3des -m cbc -pass oculto
./stegobmp -extract -p imagenmas1.bmp -out mensaje1 -steg LSBI -a 3des -m cbc -pass oculto
```

## Formato de lo oculto

Sin cifrado se oculta `tamaño || datos del archivo || extensión`, donde el tamaño ocupa 4 bytes en
big endian y la extensión empieza con `.` y termina con `\0`.

Con cifrado se oculta `tamaño del cifrado || cifrado(tamaño || datos del archivo || extensión)`.

Si el archivo no entra en el portador, el programa informa la capacidad máxima del portador para el
método elegido y cuántos bytes hacían falta.

## Errores

Los errores se informan por la salida de error estándar, junto con la sintaxis correcta cuando el problema
está en los parámetros. Códigos de salida:

| Código | Significado                                          |
|--------|------------------------------------------------------|
| 0      | Terminó bien.                                        |
| 1      | Error de archivos, del BMP, de capacidad o de cifrado. |
| 2      | Parámetros incorrectos.                              |

## Pruebas de la fase 2

Desde la raíz del repositorio:

```sh
make test
make test-cli
```

`make test` ejecuta las pruebas de capacidad, extracción, ocultamiento y límites de LSBI, sin
bibliotecas externas. `make test-cli` incluye esas pruebas y 23 casos por línea de comandos para
LSB1, LSB4 y LSBI. Comprueba hashes y tamaños del PNG oficial, regeneración idéntica con `cmp`,
archivos binarios, BMP con 1, 2 y 3 bytes de padding por fila, y errores sin crear ni sobrescribir salidas.
Los casos por CLI usan una carpeta temporal que se elimina al terminar. `cmp` forma parte de
las herramientas habituales de Ubuntu.

Para repetir la comparación de métodos:

```sh
make comparar
```

La herramienta utiliza el mismo PNG y portador de los ejemplos, verifica la recuperación y la coincidencia
con los BMP oficiales, e imprime capacidades, bytes/bits modificados, MSE y PSNR. Trabaja en memoria y
no genera imágenes en el repositorio. El análisis, los resultados y las respuestas I, II, VII y IX están en
[el informe de fase 2](informe/Fase2-LSBI.md).

## Prueba de los ejemplos

Desde la carpeta del proyecto:

```sh
./probar_ejemplos.sh
```

El script compila una copia del código con `make` en una carpeta temporal y prueba lo siguiente:

1. Extrae los ejemplos de `materiales/ejemplo/`, cada uno con su método y su cifrado (password `margarita`).
   Comprueba que salga un `.png` válido y que de `lado.bmp` no se extraiga nada.
2. Vuelve a ocultar cada `.png` en `lado.bmp` con los mismos parámetros y lo compara byte a byte con el
   archivo de la cátedra.
3. Oculta y extrae un archivo de prueba con LSB1, LSB4 y LSBI, sin cifrar y con cada combinación de
   algoritmo y modo, y compara los sha256.

Muestra OK o FALLA para cada caso y, al final, cuántos pasaron y cuáles fallaron. Si todos pasan, termina con
código 0. No deja archivos dentro del repositorio.

Al cerrar la fase 2 se aprueban **13 de 67 casos**. Los 54 restantes dependen del cifrado pendiente,
por lo que este script todavía termina con código 1. Las pruebas de `make test-cli` pasan completas.

## Estructura

```
src/stegobmp/
├── Main.java          punto de entrada y manejo de errores
├── Embedder.java      flujo de ocultamiento
├── Extractor.java     flujo de extracción
├── cli/               lectura y validación de parámetros
├── bmp/               lectura, validación y escritura de BMP
├── payload/           formato tamaño || datos || extensión
├── steg/              métodos LSB1, LSB4 y LSBI
├── crypto/            cifrado con password
└── io/                lectura y escritura de archivos
```
