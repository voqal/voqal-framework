package dev.voqal.social

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.Base64
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Draft
import com.google.api.services.gmail.model.Label
import com.google.api.services.gmail.model.Message
import com.google.api.services.gmail.model.ModifyMessageRequest
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class GmailConnection {
    companion object {
        private const val APPLICATION_NAME = "Gmail API Java Quickstart"
        private val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()
        private val SCOPES = listOf(GmailScopes.MAIL_GOOGLE_COM)

        @Throws(IOException::class)
        private fun getCredentials(HTTP_TRANSPORT: NetHttpTransport): Credential {
            val `in` =
                File("C:\\Users\\Brandon\\Desktop\\client_secret_1079188804909-7vnpqsbbb6irgikr7kj0k243t2vs2nhk.apps.googleusercontent.com.json").inputStream()
            val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(`in`))

            val flow = GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES
            )
                .setDataStoreFactory(FileDataStoreFactory(File("C:\\Users\\Brandon\\IdeaProjects\\workspace\\tokens")))
                .setAccessType("offline")
                .build()
            val receiver: LocalServerReceiver = LocalServerReceiver.Builder().setPort(8888).build()
            return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
        }

        private fun labelUnreadMessages(service: Gmail, user: String) {
            val labelName = "Test2"
            val labelsResponse = service.users().labels().list(user).execute()
            val existingLabel = labelsResponse.labels.firstOrNull { it.name == labelName }
            val labelId = existingLabel?.id ?: run {
                val newLabel = Label().apply {
                    name = labelName
                    labelListVisibility = "labelShow"
                    messageListVisibility = "show"
                }
                val createdLabel = service.users().labels().create(user, newLabel).execute()
                createdLabel.id
            }

            // Find unread messages
            val query = "is:unread in:inbox"
            val messagesResponse = service.users().messages().list(user).setQ(query).execute()
            val messages = messagesResponse.messages

            if (messages.isNullOrEmpty()) {
                println("No unread messages found.")
                return
            }

            println("Applying label '$labelName' to unread messages...")
            for (message in messages) {
                val modifyRequest = ModifyMessageRequest().apply {
                    addLabelIds = listOf(labelId)
                }
                service.users().messages().modify(user, message.id, modifyRequest).execute()
                println("Labeled message ID: ${message.id}")
            }
            println("All unread messages have been labeled with '$labelName'.")
        }

        private fun printEmailContents(service: Gmail, user: String) {
            // Find unread messages
            val query = "is:unread in:inbox"
            val messagesResponse = service.users().messages().list(user).setQ(query).execute()
            val messages = messagesResponse.messages

            if (messages.isNullOrEmpty()) {
                println("No unread messages found.")
                return
            }

            println("Printing contents of unread messages:")
            for (message in messages) {
                val fullMessage = service.users().messages().get(user, message.id).setFormat("full").execute()

                val subject = fullMessage.payload.headers.firstOrNull { it.name == "Subject" }?.value ?: "No Subject"
                val from = fullMessage.payload.headers.firstOrNull { it.name == "From" }?.value ?: "Unknown Sender"
                val body = fullMessage.payload.parts?.firstOrNull { it.mimeType == "text/plain" }?.body?.data?.let {
                    String(Base64.decodeBase64(it))
                } ?: "No Body"

                println("Subject: $subject")
                println("From: $from")
                println("Body: $body")
                println("---------------")
            }
        }

        private fun helloWorldDraft(service: Gmail, user: String) {
            val query = "is:unread in:inbox"
            val messagesResponse = service.users().messages().list(user).setQ(query).execute()
            val messages = messagesResponse.messages

            if (messages.isNullOrEmpty()) {
                println("No unread messages found.")
                return
            }

            println("Creating drafts as responses for unread emails...")
            for (message in messages) {
                val fullMessage = service.users().messages().get(user, message.id).execute()
                val threadId = fullMessage.threadId
                val to = fullMessage.payload.headers.firstOrNull { it.name == "From" }?.value ?: "unknown@example.com"
                val subject = fullMessage.payload.headers.firstOrNull { it.name == "Subject" }?.value ?: "No Subject"

                // Create the email content as a reply
                val email = createReplyEmail(to, "me", subject, "Hello world!", message.id)
                val draft = Draft().apply {
                    this.message = Message().apply {
                        raw = encodeEmail(email)
                        this.threadId = threadId // Attach to the thread
                    }
                }

                // Add draft to Gmail
                service.users().drafts().create(user, draft).execute()
                println("Draft created as a response for thread ID: $threadId")
            }
        }

        private fun createReplyEmail(
            to: String,
            from: String,
            subject: String,
            bodyText: String,
            originalMessageId: String
        ): MimeMessage {
            val session = Session.getDefaultInstance(Properties(), null)
            val email = MimeMessage(session)
            email.setFrom(InternetAddress(from))
            email.addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(to))
            email.subject = "Re: $subject"
            email.setText(bodyText)

            // Add headers for reply
            email.setHeader("In-Reply-To", originalMessageId)
            email.setHeader("References", originalMessageId)
            return email
        }

        private fun encodeEmail(email: MimeMessage): String {
            val buffer = ByteArrayOutputStream()
            email.writeTo(buffer)
            return Base64.encodeBase64URLSafeString(buffer.toByteArray())
        }

        @JvmStatic
        fun main(args: Array<String>) {
//            val user = "me"
//            labelUnreadMessages(service, user)
//            printEmailContents(service, user)
//            helloWorldDraft(service, user)
        }
    }

    val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
    val service = Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
        .setApplicationName(APPLICATION_NAME)
        .build()

    fun getUnreadEmails(): JsonArray {
        val query = "is:unread in:inbox"
        val messagesResponse = service.users().messages().list("me").setQ(query).execute()
        val messages = messagesResponse.messages

        if (messages.isNullOrEmpty()) {
            println("No unread messages found.")
            return JsonArray()
        }

        val unreadEmails = JsonArray()
        for (message in messages) {
            val fullMessage = service.users().messages().get("me", message.id).setFormat("full").execute()
            val fullMessageJson = fullMessage.toPrettyString()
            val fullJson = JsonObject(fullMessageJson)
            val body = fullMessage.payload.parts?.firstOrNull { it.mimeType == "text/plain" }?.body?.data?.let {
                String(Base64.decodeBase64(it))
            } ?: "No Body"
            fullJson.put("body", body)

            //replace headers with a map
            val headers = fullMessage.payload.headers.associate { it.name to it.value }
            fullJson.put("headers", JsonObject(headers))

            unreadEmails.add(fullJson)
        }
        return unreadEmails
    }

    fun addLabel(messageId: String, labelName: String) {
        val labelsResponse = service.users().labels().list("me").execute()
        val existingLabel = labelsResponse.labels.firstOrNull { it.name == labelName }
        val labelId = existingLabel?.id ?: run {
            val newLabel = Label().apply {
                name = labelName
                labelListVisibility = "labelShow"
                messageListVisibility = "show"
            }
            val createdLabel = service.users().labels().create("me", newLabel).execute()
            createdLabel.id
        }

        val modifyRequest = ModifyMessageRequest().apply {
            addLabelIds = listOf(labelId)
        }
        service.users().messages().modify("me", messageId, modifyRequest).execute()
        println("Labeled message ID: $messageId with '$labelName'.")
    }

    fun makeDraft(messageId: String, text: String) {
        val user = "me"
        val fullMessage = service.users().messages().get(user, messageId).execute()
        val threadId = fullMessage.threadId
        val to = fullMessage.payload.headers.firstOrNull { it.name == "From" }?.value ?: "unknown@example.com"
        val subject = fullMessage.payload.headers.firstOrNull { it.name == "Subject" }?.value ?: "No Subject"

        // Create the email content as a reply
        val email = createReplyEmail(to, "me", subject, text, messageId)
        val draft = Draft().apply {
            this.message = Message().apply {
                raw = encodeEmail(email)
                this.threadId = threadId // Attach to the thread
            }
        }

        // Add draft to Gmail
        service.users().drafts().create(user, draft).execute()
        println("Draft created as a response for thread ID: $threadId")
    }

    private fun createReplyEmail(
        to: String,
        from: String,
        subject: String,
        bodyText: String,
        originalMessageId: String
    ): MimeMessage {
        val session = Session.getDefaultInstance(Properties(), null)
        val email = MimeMessage(session)
        email.setFrom(InternetAddress(from))
        email.addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(to))
        email.subject = "Re: $subject"
        email.setText(bodyText)

        // Add headers for reply
        email.setHeader("In-Reply-To", originalMessageId)
        email.setHeader("References", originalMessageId)
        return email
    }

    private fun encodeEmail(email: MimeMessage): String {
        val buffer = ByteArrayOutputStream()
        email.writeTo(buffer)
        return Base64.encodeBase64URLSafeString(buffer.toByteArray())
    }
}
