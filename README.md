# stegobmp — Grupo 11

Trabajo práctico de implementación de Criptografía y Seguridad (72.04, ITBA).

`stegobmp` oculta un archivo cualquiera dentro de una imagen BMP de 24 bits sin compresión y recupera
lo ocultado con LSB1, LSB4 o LSBI. Opcionalmente cifra el archivo con una password antes de ocultarlo,
con AES-128/192/256 o 3DES en modo ECB, CBC, CFB (8 bits) u OFB.

## Integrantes

- Matías Romanato
- Román Berruti
- Agustín Alonso
- Agustín Brunero

## Requisitos

- Linux. La consigna utiliza WSL2 con Ubuntu 22.04.3 LTS y exige funcionamiento en pampero.
  El programa se verificó con Java 21 en WSL2 con Ubuntu 24.04 (sin cifrado) y en macOS (completo, con cifrado);
  las pruebas en Ubuntu 22.04.3 y en pampero quedan pendientes.
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

Ocultar y recuperar con cifrado 3DES en modo CBC (al extraer se usan los mismos `-a`, `-m` y `-pass`):

```sh
./stegobmp -embed -in mensaje1.txt -p imagen1.bmp -out imagenmas1.bmp -steg LSBI -a 3des -m cbc -pass oculto
./stegobmp -extract -p imagenmas1.bmp -out mensaje1 -steg LSBI -a 3des -m cbc -pass oculto
```

Extraer los ejemplos cifrados de la cátedra (password `margarita`):

```sh
./stegobmp -extract -p materiales/ejemplo/ladoLSB1aes128cbc.bmp -out aes -steg LSB1 -a aes128 -m cbc -pass margarita
./stegobmp -extract -p materiales/ejemplo/ladoLSBIdescfb.bmp -out des -steg LSBI -a 3des -m cfb -pass margarita
```

## Formato de lo oculto

Sin cifrado se oculta `tamaño || datos del archivo || extensión`, donde el tamaño ocupa 4 bytes en
big endian y la extensión empieza con `.` y termina con `\0`.

Con cifrado se oculta `tamaño del cifrado || cifrado(tamaño || datos del archivo || extensión)`. El tamaño
del cifrado también ocupa 4 bytes en big endian.

Se admite cualquier archivo, incluidos archivos vacíos, archivos sin extensión, extensiones largas y BMP
(se oculta el archivo completo, con su encabezado). Al extraer, la extensión recuperada no puede contener
`/`, `\` ni caracteres de control, para que la salida no se escriba fuera de la ruta pedida.

### Cifrado

| `-a`     | Algoritmo JCE | Clave    | Bloque e IV |
|----------|---------------|----------|-------------|
| `aes128` | AES           | 16 bytes | 16 bytes    |
| `aes192` | AES           | 24 bytes | 16 bytes    |
| `aes256` | AES           | 32 bytes | 16 bytes    |
| `3des`   | DESede (DES-EDE3, tres claves) | 24 bytes | 8 bytes |

| `-m`  | Transformación JCE          | Observaciones                                        |
|-------|-----------------------------|------------------------------------------------------|
| `ecb` | `<alg>/ECB/PKCS5Padding`    | Sin IV.                                              |
| `cbc` | `<alg>/CBC/PKCS5Padding`    |                                                      |
| `cfb` | `<alg>/CFB8/NoPadding`      | Realimentación de 8 bits; conserva la longitud.      |
| `ofb` | `<alg>/OFB/NoPadding`       | Realimentación del bloque completo; conserva la longitud. |

La clave y el IV se derivan de la password con PBKDF2-HMAC-SHA256, salt fijo de 8 bytes en cero y 10000
iteraciones. De la salida se toman primero los bytes de la clave y a continuación los del IV. Con la password
`margarita` la salida de 48 bytes es
`03db0a157acfe8de523760aa731d8122b25f8d99f3173ec0b52849f459a4c20d212420edc583a686a94d19a3497363a2`; para
AES-128 el IV es `b25f8d99f3173ec0b52849f459a4c20d` (el README de los ejemplos de la cátedra muestra por
error el IV de AES-256 para ese caso). Se usa solamente la JCE incluida en el JDK 21.

ECB y CBC agregan padding PKCS#5 (siempre, incluso si el bloque ya está alineado). La capacidad se controla
sobre el payload cifrado real, con su padding y su prefijo. Sin autenticación, en CFB y OFB una password
incorrecta se detecta porque lo descifrado no tiene el formato esperado.

Si el archivo no entra en el portador, el programa informa la capacidad máxima del portador para el
método elegido y cuántos bytes hacían falta.

## Errores

Los errores se informan por la salida de error estándar, junto con la sintaxis correcta cuando el problema
está en los parámetros. Códigos de salida:

| Código | Significado                                          |
|--------|------------------------------------------------------|
| 0      | Terminó bien.                                        |
| 1      | Error de archivos, del BMP, de capacidad o de descifrado (password, algoritmo o modo incorrectos). |
| 2      | Parámetros incorrectos.                              |

## Pruebas de la implementación

Desde la raíz del repositorio:

```sh
make test
make test-cli
```

`make test` ejecuta, sin bibliotecas externas:

- las pruebas de capacidad, extracción, ocultamiento y límites de LSBI;
- las del formato oculto: archivos vacíos, extensiones largas o UTF-8, bytes NUL y datos inválidos;
- las del cifrador: PBKDF2 para los cuatro algoritmos, 32 vectores generados con OpenSSL 3 (las 16
  combinaciones con un texto alineado y otro no alineado), longitudes cifradas de 0 a 40 bytes, ciphertext
  truncado, padding inválido, password incorrecta y los tres ejemplos cifrados de la cátedra;
- una matriz en memoria de los 3 métodos por 4 algoritmos por 4 modos (48 combinaciones).

`make test-cli` incluye esas pruebas y además:

- 23 casos por línea de comandos sin cifrado: hashes y tamaños del PNG oficial, regeneración idéntica
  con `cmp`, archivos binarios, BMP con 1, 2 y 3 bytes de padding por fila, y errores sin crear ni
  sobrescribir salidas;
- 61 casos con cifrado: extracción y regeneración con `cmp` de los ejemplos cifrados, valores por defecto
  de `-a` y `-m`, opciones ignoradas sin `-pass`, archivos vacíos, sin extensión, con extensión larga, BMP y
  con bytes NUL, capacidad exacta y excedida después del padding, passwords/modos/algoritmos incorrectos,
  ciphertexts truncados, errores de parámetros (código 2) y BMP inválidos (código 1), siempre sin
  crear ni sobrescribir salidas.

Los casos por CLI usan una carpeta temporal que se elimina al terminar. `cmp` forma parte de
las herramientas habituales de Ubuntu.

Para repetir la comparación de métodos:

```sh
make comparar
```

La herramienta utiliza el mismo PNG y portador de los ejemplos, verifica la recuperación y la coincidencia
con los BMP oficiales, e imprime capacidades, bytes/bits modificados, MSE y PSNR. Trabaja en memoria y
no genera imágenes en el repositorio. El análisis, los resultados y las respuestas I, II, VII y IX están en
[el informe de implementación y análisis](informe/ImplementacionYAnalisis.md).

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

Actualmente se aprueban **67 de 67 casos** y el script termina con código 0.

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
├── crypto/            cifrado con password (PBKDF2 + JCE)
└── io/                lectura y escritura de archivos
```
