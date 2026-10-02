package uk.co.fireburn.lochssh.ssh

import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal object PortKnocking {
    suspend fun send(host: String, steps: List<KnockSequence.Step>, pauseMillis: Long = 200) =
        withContext(Dispatchers.IO) {
            validate(steps, pauseMillis)
            if (steps.isEmpty()) return@withContext
            currentCoroutineContext().ensureActive()
            val address = InetAddress.getByName(host)
            run(steps, pauseMillis) { sendPacket(address, it) }
        }

    internal suspend fun run(steps: List<KnockSequence.Step>, pauseMillis: Long,
        send: (KnockSequence.Step) -> Unit) {
        validate(steps, pauseMillis)
        steps.forEachIndexed { index, step ->
            currentCoroutineContext().ensureActive()
            send(step)
            if (index < steps.lastIndex) delay(pauseMillis)
        }
        currentCoroutineContext().ensureActive()
    }

    private fun validate(steps: List<KnockSequence.Step>, pauseMillis: Long) {
        require(steps.size <= 32 && steps.all { it.port in 1..65535 }) { "Invalid port sequence" }
        require(pauseMillis in 0..5000) { "Pause must be between 0 and 5000 milliseconds" }
    }

    private fun sendPacket(address: InetAddress, step: KnockSequence.Step) {
        when (step.protocol) {
            KnockSequence.Protocol.UDP -> DatagramSocket().use {
                it.send(DatagramPacket(byteArrayOf(0), 1, address, step.port))
            }
            KnockSequence.Protocol.TCP -> Socket().use {
                try {
                    it.connect(InetSocketAddress(address, step.port), 200)
                } catch (_: ConnectException) {
                    // A closed port still receives the connection attempt.
                } catch (_: SocketTimeoutException) {
                    // A firewall can drop replies after receiving the attempt.
                }
            }
        }
    }
}
