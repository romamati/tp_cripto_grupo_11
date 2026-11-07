# stegobmp — Grupo 11

Trabajo práctico de implementación de Criptografía y Seguridad (72.04, ITBA).

`stegobmp` oculta un archivo cualquiera dentro de una imagen BMP de 24 bits sin compresión y recupera
lo ocultado. Los métodos disponibles son LSB1, LSB4 y LSBI. Opcionalmente, antes de ocultarlo, cifra el
archivo con una password.

## Integrantes

- Matías Romanato
- Román Berruti
- Agustín Alonso
- Agustín Brunero

## Requisitos

- Linux (probado en Ubuntu 22.04 sobre WSL2).
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

Ocultar con LSBI y 3DES en modo CBC, y después extraer:

```sh
./stegobmp -embed -in mensaje1.txt -p imagen1.bmp -out imagenmas1.bmp -steg LSBI -a 3des -m cbc -pass oculto
./stegobmp -extract -p imagenmas1.bmp -out mensaje1 -steg LSBI -a 3des -m cbc -pass oculto
```

Ocultar sin cifrar:

```sh
./stegobmp -embed -in mensaje1.txt -p imagen1.bmp -out imagenmas1.bmp -steg LSB4
```

Extraer los ejemplos de la cátedra:

```sh
./stegobmp -extract -p materiales/ejemplo/ladoLSB1.bmp -out lsb1 -steg LSB1
./stegobmp -extract -p materiales/ejemplo/ladoLSB1aes128cbc.bmp -out lsb1aes -steg LSB1 -a aes128 -m cbc -pass margarita
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
