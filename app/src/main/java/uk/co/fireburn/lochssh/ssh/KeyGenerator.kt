package uk.co.fireburn.lochssh.ssh

import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.util.Base64

data class GeneratedKey(
    val privateKeyPem: String,
    val publicKeyOpenSsh: String
)

object KeyGenerator {

    fun generate(comment: String = "lochssh"): GeneratedKey {
        return try {
            ed25519(KeyPairGenerator.getInstance("Ed25519").generateKeyPair(), comment)
        } catch (e: NoSuchAlgorithmException) {
            rsa(KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair(), comment)
        }
    }

    private fun ed25519(pair: KeyPair, comment: String): GeneratedKey {
        val spki = pair.public.encoded
            ?: throw UnsupportedOperationException("Public key export not supported")
        val pkcs8 = pair.private.encoded
            ?: throw UnsupportedOperationException("Private key export not supported")
        val spkiPrefix = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
        val pkcs8Prefix = byteArrayOf(0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20)
        require(spki.size == 44 && spki.copyOfRange(0, 12).contentEquals(spkiPrefix)) {
            "Unsupported Ed25519 public key encoding"
        }
        require(pkcs8.size == 48 && pkcs8.copyOfRange(0, 16).contentEquals(pkcs8Prefix)) {
            "Unsupported Ed25519 private key encoding"
        }
        val pubRaw = spki.copyOfRange(12, 44)
        val seed = pkcs8.copyOfRange(16, 48)
        val pubBlob = sshString("ssh-ed25519") + sshString(pubRaw)
        return GeneratedKey(
            privateKeyPem = toOpenSshPrivateKey(pubRaw, seed, comment),
            publicKeyOpenSsh = "ssh-ed25519 ${b64(pubBlob)} $comment"
        )
    }

    private fun rsa(pair: KeyPair, comment: String): GeneratedKey {
        val rsa = pair.public as RSAPublicKey
        val blob = sshString("ssh-rsa") + sshString(rsa.publicExponent.toByteArray()) + sshString(rsa.modulus.toByteArray())
        return GeneratedKey(
            privateKeyPem = toPkcs8Pem(pair),
            publicKeyOpenSsh = "ssh-rsa ${b64(blob)} $comment"
        )
    }

    private fun toOpenSshPrivateKey(pubRaw: ByteArray, seed: ByteArray, comment: String): String {
        val pubBlob = sshString("ssh-ed25519") + sshString(pubRaw)
        val priv = ByteArrayOutputStream()
        val check = SecureRandom().nextInt()
        writeUInt32(priv, check)
        writeUInt32(priv, check)
        priv.write(sshString("ssh-ed25519".toByteArray()))
        priv.write(sshString(pubRaw))
        priv.write(sshString(seed + pubRaw))
        priv.write(sshString(comment.toByteArray()))
        val need = (8 - priv.size() % 8) % 8
        for (i in 1..need) priv.write(i)

        val payload = ByteArrayOutputStream()
        payload.write("openssh-key-v1\u0000".toByteArray())
        payload.write(sshString("none".toByteArray()))
        payload.write(sshString("none".toByteArray()))
        payload.write(sshString(ByteArray(0)))
        writeUInt32(payload, 1)
        payload.write(sshString(pubBlob))
        payload.write(sshString(priv.toByteArray()))
        return wrap("OPENSSH PRIVATE KEY", payload.toByteArray())
    }

    private fun toPkcs8Pem(pair: KeyPair): String {
        val der = pair.private.encoded
            ?: throw UnsupportedOperationException("Private key export not supported")
        return wrap("PRIVATE KEY", der)
    }

    private fun wrap(label: String, der: ByteArray): String {
        val b64 = b64(der).chunked(70).joinToString("\n")
        return "-----BEGIN $label-----\n$b64\n-----END $label-----\n"
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun sshString(s: String): ByteArray = sshString(s.toByteArray())

    private fun sshString(bytes: ByteArray): ByteArray {
        val out = ByteArray(4 + bytes.size)
        out[0] = ((bytes.size shr 24) and 0xFF).toByte()
        out[1] = ((bytes.size shr 16) and 0xFF).toByte()
        out[2] = ((bytes.size shr 8) and 0xFF).toByte()
        out[3] = (bytes.size and 0xFF).toByte()
        bytes.copyInto(out, 4)
        return out
    }

    private fun writeUInt32(out: ByteArrayOutputStream, v: Int) {
        out.write((v shr 24) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write(v and 0xFF)
    }
}
