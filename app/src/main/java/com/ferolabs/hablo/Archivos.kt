package com.ferolabs.hablo

import java.io.File

/**
 * Escritura segura de los archivos de progreso (cuaderno, mazo, memoria, Aptis,
 * crucigramas), 08-10. Antes era `writeText` directo: si el teléfono se apagaba
 * a mitad, el archivo quedaba roto; al arrancar se ignoraba, la app empezaba
 * vacía y el siguiente guardado lo pisaba. Ahora se escribe a un `.tmp` y se
 * cambia de nombre (en el mismo directorio es atómico).
 */
fun File.escribirSeguro(texto: String) {
    parentFile?.mkdirs()
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(texto)
    if (!tmp.renameTo(this)) {
        // Algunos sistemas no reemplazan al renombrar: se borra y se reintenta.
        delete()
        if (!tmp.renameTo(this)) { writeText(texto); tmp.delete() }
    }
}

/**
 * Un archivo que no se pudo leer se aparta como `<nombre>.roto` (el último) en vez de
 * dejar que el siguiente guardado lo pise: así se puede recuperar a mano.
 */
fun File.apartarRoto() {
    try {
        val roto = File(parentFile, "$name.roto")
        roto.delete()
        renameTo(roto)
    } catch (_: Throwable) {
    }
}
