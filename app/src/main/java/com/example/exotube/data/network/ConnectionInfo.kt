package com.example.exotube.data.network

import android.content.Context
import android.net.ConnectivityManager

/**
 * Qué tipo de conexión hay ahora mismo, para que la calidad "Automática" de los videos sepa si
 * puede permitirse más imagen o si conviene cuidar los datos.
 */
class ConnectionInfo(context: Context) {

    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /**
     * true con datos móviles (o un wifi compartido desde otro teléfono): cada megabyte cuenta.
     *
     * Se pregunta así, y no "¿es wifi?", porque es lo que de verdad importa: Android marca como
     * "de pago" cualquier red que el usuario o la operadora limiten, sea del tipo que sea.
     * Sin conexión también devuelve true: es el lado prudente.
     */
    fun isMetered(): Boolean = connectivity?.isActiveNetworkMetered ?: true
}
