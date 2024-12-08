package com.email

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.RequestHandler
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.search.AndTerm
import jakarta.mail.search.ComparisonTerm
import jakarta.mail.search.FromStringTerm
import jakarta.mail.search.ReceivedDateTerm
import jakarta.mail.search.SearchTerm
import jakarta.mail.search.SubjectTerm
import java.util.*

class OtpReceiver : RequestHandler<Map<String, String>, String> {

    lateinit var logger: LambdaLogger

    val IMAP_SERVER = "imap.gmail.com"
    val IMAP_PORT = "993"
    val IMAP_PROTOCOL = "imap"

    private fun fetchOTP(
        mailBoxProperties: MailBoxProperties,
        credentials: MailAccountCredentials,
        emailFilter: EmailFilter
    ): String {
        val properties = getMailServerProperties(mailBoxProperties)
        val session = Session.getDefaultInstance(properties)

        val store = session.getStore(mailBoxProperties.protocol)
        var folder: Folder? = null
        try {
            // connects to the message store
            store.connect(credentials.userName, credentials.password)

            // opens the inbox folder
            folder = store.getFolder(emailFilter.folder.name)
            folder.open(Folder.READ_ONLY)

            val messages = searchEmail(folder, emailFilter)
            return if (messages.isNotEmpty()) {
                extractOTP(messages.last(), emailFilter)
            } else {
                ""
            }
        } finally {
            folder?.close(false)
            store.close()
        }
    }

    private fun searchEmail(
        folder: Folder,
        emailFilter: EmailFilter
    ): List<Message> {
        val searchTerms = mutableListOf<SearchTerm>()
        emailFilter.subject?.ifNotBlank {
            searchTerms.add(SubjectTerm(emailFilter.subject))
        }
        emailFilter.fromAddress?.ifNotBlank {
            searchTerms.add(FromStringTerm(emailFilter.fromAddress))
        }
        emailFilter.startTime?.let {
            val start = Date(it)
            searchTerms.add(ReceivedDateTerm(ComparisonTerm.GE, start))
        }
        return folder.search(AndTerm(searchTerms.toTypedArray())).asList()
    }

    private fun extractOTP(message: Message, emailFilter: EmailFilter): String {
        emailFilter.startTime?.let {
            if (message.receivedDate.before(Date(it))) {
                return ""
            }
        }
        val contentType = message.contentType
        if (contentType.contains("TEXT/PLAIN") or contentType.contains("TEXT/HTML")) {
            val content = message.content.toString()
            val otpIndex: Int = content.indexOf(emailFilter.pattern!!) + emailFilter.pattern.length
            return content.substring(otpIndex, otpIndex + 6)
        }
        return ""
    }

    private fun getMailServerProperties(mailBoxProperties: MailBoxProperties): Properties {
        val properties = Properties()

        // server setting
        properties[String.format("mail.%s.host", mailBoxProperties.protocol)] = mailBoxProperties.host
        properties[String.format("mail.%s.port", mailBoxProperties.protocol)] = mailBoxProperties.port

        // SSL setting
        properties.setProperty(
            String.format("mail.%s.socketFactory.class", mailBoxProperties.protocol),
            "javax.net.ssl.SSLSocketFactory"
        )
        properties.setProperty(
            String.format("mail.%s.socketFactory.fallback", mailBoxProperties.protocol),
            "false"
        )
        properties.setProperty(String.format("mail.%s.socketFactory.port", mailBoxProperties.protocol), mailBoxProperties.port)
        return properties
    }

    private inline fun String.ifNotBlank(func: (String) -> Unit) {
        if (this.isNotBlank()) {
            with(this) { func(this) }
        }
    }

    data class EmailFilter(val folder: MailFolder, val fromAddress: String? = null, val subject: String? = null, val pattern: String? = null, val startTime: Long? = null)

    data class MailBoxProperties(
        val protocol: String,
        val host: String,
        val port: String
    )

    data class MailAccountCredentials(val userName: String, val password: String)

    enum class MailFolder {
        INBOX,
        SENT
    }

    override fun handleRequest(event: Map<String, String>?, context: Context?): String {
        context?.let {
            logger = context.logger
        }

        event?.let {
            val emailAddress = it.get("email_address")
            val password = it.get("password")
            if (emailAddress.isNullOrBlank() || password.isNullOrBlank()) {
                logger.log("Could not get enough credentials to fetch otp")
                return ""
            }
            val fromAddress = it.get("from_address")
            val subject = it.get("subject")
            val pattern = it.get("pattern")
            val startTime = it.get("start_time")

            val otp = fetchOTP(
                mailBoxProperties = MailBoxProperties(IMAP_PROTOCOL, IMAP_SERVER, IMAP_PORT),
                credentials = MailAccountCredentials(emailAddress, password),
                emailFilter = EmailFilter(
                    folder = MailFolder.INBOX,
                    fromAddress = fromAddress,
                    subject = subject,
                    pattern = pattern,
                    startTime = startTime?.toLongOrNull()
                )
            )

            return otp
        }
        logger.log("Did not receive any event containing details")

        return ""
    }
}


fun main() {
    val EMAIL_ADDRESS = "blabla@gmail.com"
    val EMAIL_PASSWORD = "123456"
    val IMAP_SERVER = "imap.gmail.com"
    val IMAP_PORT = "993"
    val IMAP_PROTOCOL = "imap"

//    val otp = EmailReceiver.fetchOTP(
//        mailBoxProperties = EmailReceiver.MailBoxProperties(IMAP_PROTOCOL, IMAP_SERVER, IMAP_PORT),
//        credentials = EmailReceiver.MailAccountCredentials(EMAIL_ADDRESS, EMAIL_PASSWORD),
//        emailFilter = EmailReceiver.EmailFilter(
//            folder = EmailReceiver.MailFolder.INBOX,
//            fromAddress = "bla-bla@gmail.com",
//            subject = "xxxx subject",
//            pattern = "One Time Password (OTP) for your login is ",
//            startTime = currentTimeMillis()
//        )
//    )
//    print(otp)

}
