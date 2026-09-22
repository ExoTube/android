package com.example.exotube.player

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Traduce nombres a direcciones IP poniendo primero las IPv4.
 *
 * YouTube ata cada dirección de video a la IP que la pidió, y yt-dlp la pide siempre por IPv4
 * (ver `YtDlpCatalog.directUrls`). Si el reproductor la descargara por IPv6 —lo que Android
 * prefiere cuando hay las dos, cosa habitual con datos móviles—, YouTube vería otra IP y
 * respondería "prohibido". Poniendo la IPv4 delante, las dos partes salen por la misma.
 *
 * Las IPv6 no se tiran: se quedan detrás, por si en esa red la IPv4 no funcionara.
 */
internal object Ipv4FirstDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> = preferIpv4(Dns.SYSTEM.lookup(hostname))
}

/** El orden sin tocar la red, para poder probarlo con un test. Conserva el orden original dentro de cada tipo. */
internal fun preferIpv4(addresses: List<InetAddress>): List<InetAddress> =
    addresses.sortedBy { if (it is Inet4Address) 0 else 1 }
