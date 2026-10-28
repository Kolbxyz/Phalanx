package org.phalanx.android.core.preview

import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/**
 * TLS over sockets already connected to a public address, checked on the address the socket
 * actually reached. A host checked by name can resolve again to another address by the time the
 * connection opens (DNS rebinding); this refuses it before the handshake sends a byte.
 * Unconnected sockets are not offered, so no path skips the check.
 */
internal class PublicOnlySocketFactory(
    private val base: SSLSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> = base.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = base.supportedCipherSuites

    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        base.createSocket(checked(socket), host, port, autoClose)

    override fun createSocket(host: String, port: Int): Socket = checked(base.createSocket(host, port))

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        checked(base.createSocket(host, port, localHost, localPort))

    override fun createSocket(host: InetAddress, port: Int): Socket = checked(base.createSocket(host, port))

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        checked(base.createSocket(address, port, localAddress, localPort))

    private fun checked(socket: Socket): Socket {
        val address = socket.inetAddress
        if (address == null || address.isPrivate()) {
            socket.close()
            throw IOException("Refused host")
        }
        return socket
    }
}

/** On this device's own networks: loopback, LAN, link-local, multicast, unique local and carrier NAT. */
internal fun InetAddress.isPrivate(): Boolean {
    val first = address[0].toInt() and BYTE
    // IPv6 unique local addresses, fc00::/7, which isSiteLocalAddress does not cover.
    val uniqueLocal = this is Inet6Address && (first and ULA_MASK) == ULA_PREFIX
    // Carrier-grade NAT, 100.64.0.0/10, as private as 10/8 in practice.
    val sharedNat = address.size == IPV4_BYTES && first == CGNAT_FIRST &&
        (address[1].toInt() and CGNAT_MASK) == CGNAT_SECOND
    return isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress ||
        isMulticastAddress || uniqueLocal || sharedNat
}

private const val IPV4_BYTES = 4
private const val ULA_MASK = 0xfe
private const val ULA_PREFIX = 0xfc
private const val CGNAT_FIRST = 100
private const val CGNAT_MASK = 0xc0
private const val CGNAT_SECOND = 64
private const val BYTE = 0xff
