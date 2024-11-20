package dev.voqal.social

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.Base64
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Draft
import com.google.api.services.gmail.model.Label
import com.google.api.services.gmail.model.Message
import com.google.api.services.gmail.model.ModifyMessageRequest
import com.intellij.openapi.project.Project
import dev.voqal.services.getVoqalLogger
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.io.ByteArrayOutputStream
import java.util.*
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class GmailConnection(project: Project, var accessToken: String) {

    companion object {
        private val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()
    }

    private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
    private val requestInitializer = HttpRequestInitializer { request: HttpRequest ->
        request.headers = HttpHeaders().apply {
            authorization = "Bearer $accessToken"
        }
    }
    private val service = Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, requestInitializer).build()
    private val log = project.getVoqalLogger(this::class)

    fun getUnreadEmails(maxResults: Long = 50): JsonArray {//todo: exclude emails with drafts
        val query = "is:unread in:inbox"
        val messagesResponse = service.users().messages()
            .list("me")
            .setQ(query)
            .setMaxResults(maxResults)
            .execute()
        val messages = messagesResponse.messages

        if (messages.isNullOrEmpty()) {
            log.debug { "No unread messages found" }
            return JsonArray()
        }
        log.debug { "Found ${messages.size} (max=$maxResults) unread messages" }

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
        log.debug { "Labeled message ID: $messageId with '$labelName'." }
    }

    fun makeDraft(messageId: String, text: String) {
        val user = "me"
        val fullMessage = service.users().messages().get(user, messageId).execute()
        val threadId = fullMessage.threadId
        val to = fullMessage.payload.headers.firstOrNull { it.name == "From" }?.value ?: "unknown@example.com"
        val subject = fullMessage.payload.headers.firstOrNull { it.name == "Subject" }?.value ?: "No Subject"

        // Create the email content as a reply
        val email = createReplyEmail(to, "me", subject, text, messageId)
        val draftContent = Draft().apply {
            this.message = Message().apply {
                raw = encodeEmail(email)
                this.threadId = threadId // Attach to the thread
            }
        }

        // Check for existing drafts in the thread
        val draftsList = service.users().drafts().list(user).execute().drafts
        val existingDraft = draftsList?.find { draft ->
            val draftMessage = service.users().drafts().get(user, draft.id).execute().message
            draftMessage.threadId == threadId
        }

        if (existingDraft != null) {
            // Update the existing draft
            service.users().drafts().update(user, existingDraft.id, draftContent).execute()
            log.debug { "Draft updated for thread ID: $threadId" }
        } else {
            // Create a new draft if no existing draft is found
            service.users().drafts().create(user, draftContent).execute()
            log.debug { "Draft created as a response for thread ID: $threadId" }
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
}
