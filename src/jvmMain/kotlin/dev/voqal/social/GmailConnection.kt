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
import com.google.api.services.gmail.model.Label
import com.google.api.services.gmail.model.ModifyMessageRequest
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

class GmailConnection {
    companion object {
        private const val APPLICATION_NAME = "Gmail API Java Quickstart"
        private val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()
        private const val TOKENS_DIRECTORY_PATH = "tokens"
        private val SCOPES = listOf(GmailScopes.MAIL_GOOGLE_COM)

        @Throws(IOException::class)
        private fun getCredentials(HTTP_TRANSPORT: NetHttpTransport): Credential {
            val `in` = File("C:\\Users\\Brandon\\Desktop\\client_secret_1079188804909-7vnpqsbbb6irgikr7kj0k243t2vs2nhk.apps.googleusercontent.com.json").inputStream()
            val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(`in`))

            val flow = GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES
            )
                .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
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

        @JvmStatic
        fun main(args: Array<String>) {
            val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
            val service = Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build()

            val user = "me"

            val listResponse = service.users().labels().list(user).execute()
            val labels = listResponse.labels
            if (labels.isEmpty()) {
                println("No labels found.")
            } else {
                println("Labels:")
                for (label in labels) {
                    println("- ${label.name}")
                }
            }

            if (labels.none { it.name == "Test2" }) {
                val label = Label().apply {
                    name = "Test2"
                    labelListVisibility = "labelShow"
                    messageListVisibility = "show"
                }
                service.users().labels().create(user, label).execute()
                println("Label 'Test2' created.")
            } else {
                println("Label 'Test2' already exists.")
            }

            labelUnreadMessages(service, user)
            printEmailContents(service, user)
        }
    }
}
