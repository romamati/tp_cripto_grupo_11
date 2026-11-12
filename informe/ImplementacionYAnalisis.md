# Implementación y análisis de stegobmp

Revisión del 12 de noviembre de 2026. Este documento describe las funcionalidades
implementadas, el trabajo pendiente y las validaciones realizadas. Desarrolla las
preguntas I, II, VII, VIII y IX de la consigna con resultados del ocultamiento y la
extracción, con y sin cifrado.

## Estado de la implementación

### Funcionalidades implementadas

- Programa Java 21, compilación con `make` y ejecución por consola mediante `stegobmp`.
- Lectura de portadores BMP de 24 bits sin compresión y uso del cuerpo en orden
  físico BGR, incluidos los bytes de padding de las filas.
- Ocultamiento y extracción con LSB1, LSB4 y LSBI, comprobación de capacidad y
  formato de tamaño big endian, contenido y extensión terminada en NUL.
- Validación de parámetros, valores por defecto y mensajes de error con los
  códigos de salida documentados en el README.
- Cifrado y descifrado con la JCE del JDK: AES-128/192/256 y 3DES (DES-EDE3) en
  los modos ECB, CBC, CFB8 y OFB, con clave e IV derivados por PBKDF2-HMAC-SHA256,
  salt de ocho bytes cero y 10000 iteraciones. Se oculta
  `tamaño del cifrado || cifrado(tamaño || archivo || extensión)`.
- Archivos vacíos, extensiones de cualquier longitud y secretos BMP completos, con
  y sin cifrado. La extensión recuperada se valida para que no contenga
  separadores de carpetas ni caracteres de control.
- Pruebas unitarias y por CLI, reproducción byte a byte de los seis ejemplos
  oficiales, vectores de cifrado contrastados con OpenSSL y comparación
  reproducible de capacidad y distorsión con `make comparar`.

### Funcionalidades y validaciones pendientes

- Completar el estegoanálisis y la recuperación reproducible de los cuatro
  archivos del grupo, incluido el método adicional, los mensajes anidados y el video.
- Desarrollar las respuestas III, IV, V, VI y X e integrarlas con este
  análisis en el informe completo, con fuentes LaTeX y PDF versionado.
- Validar el programa integrado en Ubuntu 22.04.3/Java 21 y en pampero, conservando
  evidencia de versiones, comandos y resultados.

## Fuentes

- [Consigna del TP](../materiales/consigna.pdf), secciones 5.3, 5.4, 7 y 10.
- Majeed, M. A. y Sulaiman, R. (2015),
  [An Improved LSB Image Steganography Technique using bit-inverse in 24 bit colour image](../materiales/paper_lsbi.pdf),
  JATIT, 80(2), páginas 342-348. Las páginas citadas abajo son las impresas en el paper.
- [Contrato de LSBI](../materiales/LSBI.md),
  [implementación](../src/stegobmp/steg/Lsbi.java) y
  [herramienta de comparación](../tools/CompareStegMethods.java).

## I. Análisis del documento científico

### a. Organización formal

El paper sigue una estructura reconocible: resumen y palabras clave,
introducción, presentación del LSB estándar y del color de 24 bits, técnica
propuesta, pseudocódigo, resultados experimentales, discusión y referencias.
Los ejemplos y las figuras permiten seguir la intención de reducir la
alteración del portador mediante inversiones selectivas.

La separación entre técnica, algoritmo y resultados es útil, pero faltan detalles
para reproducir exactamente el procedimiento: ubicación y codificación de los
indicadores, delimitación del mensaje, alcance de los conteos e inversiones, y
tratamiento de casos límite. En la sección experimental se anuncian tres imágenes,
pero la figura 2 y la tabla 1 incluyen seis; la tabla 2 compara tres. También se
usan cifras de otros trabajos sin detallar en esa comparación todas las condiciones
de carga y selección de posiciones. Por eso, sus PSNR no bastan para establecer
una mejora universal en condiciones equivalentes.

### b. Descripción del algoritmo

La idea central puede reconstruirse de las secciones 5 y 6, páginas 344-346:

1. Seleccionar componentes azules y verdes.
2. Agruparlas por el patrón formado por los bits 2 y 1, dejando el LSB como bit 0.
3. Insertar los bits del mensaje mediante LSB1 y contar, por patrón, cambios y coincidencias.
4. Invertir los LSB de un patrón si los cambios son mayoría estricta.
5. Guardar qué patrones se invirtieron para deshacer esa decisión durante la extracción.

La decisión debe comparar cada LSB original con el bit que se iba a insertar.
Recontar solamente las frecuencias de los patrones antes y después no alcanza:
los bits 2 y 1 no cambian al escribir el bit 0. El pseudocódigo mezcla las variables
del portador y del resultado sin precisar suficientemente su conservación.

El TP completa la especificación: formato `tamaño || archivo || extensión`, tamaños
en big endian, cuatro indicadores antes del mensaje y recorrido físico BGR. En
la implementación, los indicadores ocupan los primeros cuatro bytes del cuerpo y
el mensaje empieza en el índice 4, omitiendo los índices `i % 3 == 2`.

El paper nombra portadores `.jpg` en sus experimentos, pero no precisa el formato
de almacenamiento de la salida. Al trabajar sobre LSB de componentes, una
recodificación con pérdida puede alterar lo ocultado. El TP evita esa ambigüedad
al exigir BMP de 24 bits sin compresión.

### c. Notación, errores y contradicciones

| Observación | Ubicación | Interpretación adoptada |
| --- | --- | --- |
| La lista de cuatro patrones repite `10` y omite `01`. | Página 344, sección 5, paso 1. | Los patrones son `00`, `01`, `10`, `11`. |
| El ejemplo afirma que no puede compararse un patrón con una sola ocurrencia. | Página 345, paso 4.b. | Sí puede compararse su LSB original con el bit a insertar: se aplica la misma regla de mayoría estricta, incluso `1 > 0`. |
| Se alternan los términos píxel y valor de color, aunque se operan componentes de 8 bits. | Páginas 343-345. | Los conteos de la implementación son de bytes/componentes, no de píxeles RGB completos. |
| Se atribuyen 2881 bits a un mensaje de 226 caracteres. | Página 346, sección 7. | A 8 bits por carácter serían 1808 bits; 2881 tampoco es múltiplo de 8. No se usa esa cifra para dimensionar el payload. |
| Se describen imágenes de dimensiones `M × M` y `N × N` y luego se promedian diferencias por coordenadas comunes. | Página 347, explicación de MSE. | Se comparan dos imágenes de iguales dimensiones; aquí el promedio incluye las tres componentes de cada píxel. |
| Se plantea no afectar la capacidad, pero se omite el canal rojo. | Página 343, sección 2, y página 345. | Frente al LSB1 del TP que usa BGR, LSBI dispone de aproximadamente dos tercios de la capacidad, menos el registro de indicadores. |

Además, no se define explícitamente el caso de un patrón ausente. La
implementación deja el indicador en cero en ese caso y en un empate, en
concordancia con la condición de invertir solo cuando `cambiados > sinCambios`.

## II. Comparación de LSB1, LSB4 y LSBI

### Experimento y reproducción

Se utilizó el mismo PNG de los ejemplos de la cátedra, recuperado de
`ladoLSBI.bmp`, y el mismo portador `lado.bmp`, sin cifrado. La medición se ejecutó
con Java 21.0.13.0.3 en WSL2, Ubuntu 24.04.

| Dato | Valor |
| --- | --- |
| Portador | `materiales/ejemplo/lado.bmp`, 640 × 480, 24 bits, sin compresión ni padding de filas |
| Tamaño de cada BMP | 921654 bytes: 54 de encabezado y 921600 de cuerpo |
| SHA-256 del portador | `fee3bdabe02a5d7a178955864106a923dace797bc45a642c5bfd54127b742884` |
| Contenido oculto | PNG de 44886 bytes |
| SHA-256 del PNG | `b93be15382f35f099ca286241dd7bb6e681d5bce8d315ac0b6240f0b516f549d` |
| Payload en los tres métodos | 44895 bytes: 4 de tamaño, 44886 del archivo y 5 de extensión `.png` más NUL |
| Sobrecarga adicional de LSBI | 4 bits de indicadores en 4 bytes del portador |

Desde la raíz del repositorio:

```sh
make comparar
make test-cli
```

`make comparar` ejecuta los tres métodos sobre copias en memoria del cuerpo,
verifica la recuperación del PNG y la coincidencia con el cuerpo de cada BMP
oficial, y calcula las tablas siguientes. El encabezado no participa de las
operaciones esteganográficas. `make test-cli` verifica también los BMP completos
mediante la interfaz real del programa y `cmp`, con archivos en una carpeta
temporal. No se agregan los BMP generados al repositorio.

### Resultados cuantitativos

| Método | Capacidad payload | Bytes modificados | Bits modificados | Delta máximo | MSE | PSNR (dB) |
| --- | --- | --- | --- | --- | --- | --- |
| LSB1 | 115200 | 190182 | 190182 | 1 | 0.206361 | 54.9845 |
| LSB4 | 460800 | 85370 | 205965 | 15 | 8.763059 | 38.7042 |
| LSBI | 76799 | 170852 | 170852 | 1 | 0.185386 | 55.4500 |

La capacidad corresponde al payload completo. Para un PNG sin cifrado, con la
extensión de este experimento, el máximo contenido de archivo sería de 115191,
460791 y 76790 bytes, respectivamente, al descontar los nueve bytes de formato.
El tamaño físico del portador no aumenta en ninguno de los tres casos.

Un byte modificado es una componente B, G o R cuyo valor cambió. Los bits
modificados se cuentan sobre el XOR de cada par de bytes. El delta máximo es la
mayor diferencia absoluta entre valores de componente, interpretados sin signo.
El MSE se calcula sobre las 921600 componentes del cuerpo, que en este portador
son exactamente los datos de píxeles, excluyendo el encabezado:

```text
MSE = sum((componente_original - componente_resultante)^2) / 921600
PSNR = 10 * log10(255^2 / MSE)
```

MSE y PSNR son medidas de distorsión de este experimento, no pruebas de
indetectabilidad estadística ni de seguridad criptográfica. Tampoco son una
reproducción de los PSNR del paper: cambian el portador y la longitud del mensaje.

Los bytes modificados se distribuyen así:

| Método | Azul | Verde | Rojo |
| --- | --- | --- | --- |
| LSB1 | 63231 | 63610 | 63341 |
| LSB4 | 28437 | 28490 | 28443 |
| LSBI | 85551 | 85301 | 0 |

En este caso LSBI no modifica ningún rojo: el indicador del índice 2 ya tenía el
valor final requerido. En otros portadores ese byte rojo sí puede cambiar; los
indicadores usan los tres canales.

### Resultado visual y ventajas/desventajas

En la comparación visual a escala 1:1, LSB1 y LSBI conservan una apariencia muy
similar al original. En LSB4 se observa un grano leve en la franja inferior clara,
donde comienza la inserción por el orden físico del BMP. Esta observación sobre
una imagen no reemplaza un ensayo perceptual controlado.

| Método | Ventajas | Desventajas |
| --- | --- | --- |
| LSB1 | Implementación sencilla; una pasada de inserción; cada componente cambia como máximo una unidad. | Menor capacidad que LSB4; en este caso modifica 190182 componentes y altera los tres canales. |
| LSB4 | Cuatro veces la capacidad de LSB1; usa un tramo menor del portador para el mismo mensaje. | Una componente puede variar hasta 15 unidades; aunque modifica menos bytes, cambia más bits y produce mayor MSE en este caso. |
| LSBI | Reduce los cambios por patrón respecto de LSB1 en las mismas posiciones B/G; en el experimento modifica menos componentes y obtiene mayor PSNR que LSB1. | Menor capacidad que ambos métodos; requiere conteos, una pasada de inversión y cuatro indicadores; su calidad global depende del mensaje y del portador. |

LSB4 muestra por qué contar solo bytes modificados puede resultar engañoso:
85370 es menos que en los otros métodos, pero la magnitud de sus alteraciones es
mayor. LSBI ahorra 19330 componentes modificadas frente al LSB1 de este ejemplo,
a cambio de disponer de menos capacidad.

## VII. Por qué la inversión representa una mejora

La mejora demostrable se obtiene al fijar las mismas posiciones y los mismos
bits del mensaje. Para un patrón `p`, sean `c_p` los LSB que cambiarían al insertar
normalmente y `u_p` los que quedarían iguales. Sin inversión hay `c_p` cambios;
con inversión hay `u_p`, porque se intercambian cambios y coincidencias. La
regla selecciona el menor de los dos:

```text
cambios_p = min(c_p, u_p)
ahorro_p = c_p - min(c_p, u_p) = max(0, c_p - u_p)
```

Así, la inversión nunca aumenta los cambios en los bytes del mensaje respecto de
LSB1 en esas mismas posiciones. Si hay mayoría estricta de cambios, los reduce;
si hay empate, no aporta ahorro y no se aplica. Como solo cambia el LSB, cada
cambio tiene error cuadrático uno por componente.

El control B/G en las mismas posiciones del experimento da:

| Patrón | Sin cambios | Cambiados sin inversión | Cambiados con inversión |
| --- | --- | --- | --- |
| 00 | 32028 | 32367 | 32028 |
| 01 | 32263 | 32650 | 32263 |
| 10 | 34980 | 35119 | 34980 |
| 11 | 71581 | 88172 | 71581 |

El control sin inversión produciría 188308 cambios y LSBI produce 170852:
un ahorro de 17456, equivalente al 9.2699 %. Los cuatro indicadores ya coinciden
con los LSB originales del portador, por lo que aquí no agregan cambios.

La garantía se limita a esa comparación: guardar indicadores puede introducir
hasta cuatro cambios adicionales y el LSB1 del TP utiliza otras posiciones,
incluido el rojo. Por eso no se deduce que LSBI siempre tenga mayor PSNR que ese
LSB1 para cualquier mensaje y portador. El resultado favorable de la sección II
es una medición concreta.

Omitir el rojo y publicar los indicadores no impide que alguien que conoce el
método recupere el mensaje. Una distorsión pequeña puede ayudar a que pase
inadvertido visualmente, pero no demuestra resistencia al estegoanálisis, ni
robustez frente a transformaciones de imagen, ni confidencialidad del contenido.
Esa función la cumple el cifrado con password, que se aplica antes de ocultar.

## VIII. Otras formas de guardar los patrones invertidos

La implementación entregada respeta la consigna: los cuatro indicadores ocupan el
LSB de los primeros cuatro bytes del cuerpo (índices 0 a 3, patrones `00`, `01`,
`10` y `11`) y el mensaje empieza en el índice 4. Lo que sigue es un análisis de
alternativas; ninguna se implementó, porque cambiarían el formato que deben leer
los archivos de la cátedra.

Cualquier alternativa tiene que respetar dos condiciones. La primera es que el
extractor pueda leer los indicadores antes de interpretar el mensaje, porque sin
ellos ni siquiera puede leer el tamaño. La segunda es que guardarlos no altere los
bits 1 y 2 de los bytes del mensaje, que determinan el patrón de cada byte. La
inversión solo toca el bit 0, y por eso el extractor puede recalcular el patrón
sobre el portador modificado.

| Alternativa | Localización y lectura | Capacidad | Distorsión | Información auxiliar |
| --- | --- | --- | --- | --- |
| LSB de los cuatro primeros bytes (implementada). | Fija, al inicio; se lee primero. | Usa cuatro bytes del portador, que no son todos B/G. | Hasta 4 componentes con error ±1. | Ninguna. |
| LSB de los cuatro últimos bytes del cuerpo. | Fija; se conoce el tamaño del cuerpo por el encabezado, así que se puede leer antes del mensaje. | La misma; el mensaje puede empezar en el índice 0. | La misma. | Ninguna. Si el mensaje llena el portador, el final debe reservarse y la capacidad lo descuenta. |
| LSB a continuación del último bit del mensaje. | Variable. | La misma. | La misma. | Circular: para ubicarlos hay que conocer el tamaño, pero el tamaño está guardado con inversión. Haría falta guardar el tamaño sin invertir o duplicar información. |
| LSB de cuatro componentes rojas, que LSBI no usa para el mensaje. | Fija, por ejemplo índices 2, 5, 8 y 11. | El mensaje empieza en el índice 0 y no pierde bytes B/G. | Hasta 4 componentes rojas con error ±1. | Ninguna. Modifica componentes rojas, que LSBI deja intactas. |
| Los cuatro bits juntos en un byte, con LSB4. | Fija; un único byte. | Libera 3 bytes del portador. | Un error de hasta 15 en una componente (error cuadrático hasta 225), en lugar de cuatro errores de ±1. Es un punto más llamativo. | Ninguna. |
| Los cuatro bits en un byte completo, con LSB1. | Fija. | Peor: el byte ocupa 8 bytes del portador, no 4. | Hasta 8 componentes con error ±1. | Ninguna. |
| Campos reservados del encabezado BMP (`bfReserved1/2`). | Fija, fuera de los píxeles. | No consume cuerpo. | Ninguna sobre la imagen. | Ninguna, pero esos campos deberían valer cero: un valor distinto delata el ocultamiento y otros programas pueden reescribirlos. |
| Registro externo (junto con la password o por otro canal). | Fuera del archivo. | Máxima. | Ninguna. | Exige un canal adicional: el portador deja de ser autosuficiente y perder ese dato impide extraer. |
| No guardarlos y probar las 16 combinaciones. | No hay registro. | Máxima. | Ninguna. | Requiere validar cada candidato. Con formato `tamaño || datos || extensión` sin firma puede haber falsos positivos. Con cifrado sin autenticación (CFB, OFB) se descifra 16 veces y se valida solo por estructura. |

Ubicar los indicadores al final del cuerpo o en componentes rojas mantiene el
costo de la versión implementada y evita mezclar los indicadores con el inicio del
mensaje. Empaquetarlos con LSB4 ahorra tres bytes a cambio de una distorsión
localizada mayor. Un registro externo o la búsqueda exhaustiva eliminan la
distorsión, pero dependen de información fuera del portador o de una validación que
el formato del TP no garantiza. Para mayor robustez se podrían repetir los
indicadores (por ejemplo, tres copias con voto por mayoría) a costa de más bytes
modificados. Con cuatro bits, todas las variantes tienen un efecto marginal sobre la
capacidad: la diferencia real está en la localización, en cuán detectable es el
cambio y en la dependencia de información auxiliar.

## IX. Dificultades de implementación y decisiones tomadas

| Dificultad | Solución y evidencia |
| --- | --- |
| Traducir una descripción ambigua a reglas deterministas. | Mayoría estricta, empate y patrón ausente sin inversión, y aplicación de la regla también a ocurrencias únicas. Los tests cubren esos casos y las 16 combinaciones de indicadores. |
| Separar los cuatro indicadores del mensaje y conservar la alineación BGR. | Índices relativos al cuerpo, mensaje desde 4 y salto global `i % 3 == 2`. Reiniciar la cuenta después de los indicadores daba un tamaño inválido de 109078 bytes en el ejemplo oficial. |
| Conservar el valor original al contar los cambios. | Se compara el LSB antes de escribirlo. Se acumulan cuatro conteos de cambios y cuatro de coincidencias; luego se invierten solo las posiciones usadas. No hace falta una copia adicional del cuerpo dentro de `embed`. |
| Evitar invertir el resto del portador o incluirlo en las estadísticas. | Los recorridos se limitan a los bits del payload. Se comprueba que la cola, los rojos omitidos y los siete bits superiores de todos los bytes se conserven. |
| Calcular la capacidad y tratar cuerpos pequeños sin errores de redondeo o desbordamiento. | Se descuentan los indicadores y los rojos con divisiones enteras antes de convertir bits a bytes completos. Se contrastan 100001 longitudes con un recorrido independiente y valores cercanos al máximo de `int`; también se prueba capacidad exacta y rechazo sin modificaciones para cuerpos de 0 a 512 bytes. |
| Manejar los bytes con signo de Java. | Las máscaras delimitan el LSB y los bits de patrón. Los tests usan todos los valores `0x00..0xFF`, incluidos los que Java representa con signo negativo. Las métricas convierten las componentes a valores sin signo. |
| Mantener el formato completo del secreto y no cortar ante bytes nulos del contenido. | LSBI opera sobre todo el payload y devuelve todos los bytes recuperables. `PayloadFormat` interpreta tamaño big endian y extensión terminada en NUL; el PNG oficial se verifica por tamaño y SHA-256. |
| Incluir el padding de filas sin agregar metadatos a `StegMethod`. | Se recorre linealmente todo el cuerpo. La prueba CLI genera BMP con 1, 2 y 3 bytes de padding por fila, comprueba que se utilizan esos bytes y recupera el archivo correctamente con los tres métodos. |
| Asegurar interoperabilidad y no depender solo de una ida y vuelta propia. | Se reproducen los tres ejemplos oficiales sin cifrado con igualdad byte a byte mediante `cmp`; se verifica el hash conocido del PNG y la conservación del encabezado. |
| El README de la cátedra indica para el ejemplo AES-128 el IV que corresponde a AES-256 y una clave de 16 bytes con la etiqueta de 32. | La clave y el IV se toman en forma contigua de la salida de PBKDF2: con AES-128 el IV son los bytes 16 a 31. Con esa separación se recuperan los tres ejemplos cifrados y su recifrado coincide byte a byte. El IV copiado del README daba un tamaño interior inválido. No se agregó un fallback entre IVs. |
| Elegir la realimentación correcta de cada modo. | `AES/CFB/NoPadding` de la JCE usa 128 bits de realimentación; la consigna pide 8, por lo que se usa `CFB8`. OFB usa el bloque completo: 128 bits en AES y 64 en 3DES. Los nombres se fijan en `CipherMode` y se contrastan con vectores de OpenSSL (`aes-128-cfb8`, `des-ede3-ofb`, etc.). |
| Calcular la capacidad con cifrado. | Se controla sobre el payload exterior real. ECB y CBC agregan un bloque completo de padding cuando el bloque interior ya está alineado; en un portador de prueba con capacidad LSBI de 42 bytes, 22 bytes de datos entran con AES-CBC y 23 necesitan 52. |
| Detectar ciphertexts inválidos sin autenticación. | Longitud no múltiplo de bloque y padding inválido se informan como error de descifrado. La JCE acepta en silencio un ciphertext vacío con padding, por lo que se rechaza en forma explícita. En CFB y OFB una password incorrecta solo se detecta porque el formato interior queda inválido; no se agregó un MAC porque rompería la compatibilidad. |
| No limitar archivos válidos ni abrir rutas inesperadas. | Se admiten archivos vacíos y extensiones de cualquier longitud: el terminador se busca en todo lo recuperable. La extensión se decodifica como UTF-8 estricto y se rechazan `/`, `\` y caracteres de control, para que no pueda escribir fuera del destino. |

## Validación actual

- `make test` ejecuta las pruebas de LSBI, del formato de payload, del cifrador
  (PBKDF2, 32 vectores de OpenSSL, longitudes, errores y ejemplos oficiales) y la
  matriz en memoria de 48 combinaciones de método, algoritmo y modo.
- `make test-cli` agrega 23 casos por CLI sin cifrado y 61 con cifrado.
- `bash probar_ejemplos.sh` aprueba 67 de 67 casos y termina con código 0.
- `make comparar` reproduce las métricas y comprueba que los tres resultados
  coincidan con sus referencias y recuperen el mismo PNG.
- Resultados registrados el 12 de noviembre de 2026 con OpenJDK 21.0.13.0.3
  (Homebrew) en macOS 26 (Darwin 25.6, arm64), sobre una copia limpia de los
  archivos del repositorio. Los vectores de referencia se generaron con OpenSSL 3.6.4.
- La verificación en Ubuntu 22.04.3/WSL2 y en pampero sigue pendiente para el cierre
  integral del TP.

El informe final debe reunir este análisis, las respuestas pendientes y la
evidencia del estegoanálisis y de la validación integral del programa.
