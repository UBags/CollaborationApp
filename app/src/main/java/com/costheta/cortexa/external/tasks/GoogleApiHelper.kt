package com.costheta.cortexa.external.tasks

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes

object GoogleApiHelper {
    /**
     * Creates and returns a Google Tasks service object.
     *
     * @param context The application context.
     * @param account The Google account to authenticate with.
     * @return A new instance of the Tasks service.
     */
    fun getTasksService(context: Context, account: com.google.android.gms.auth.api.signin.GoogleSignInAccount): Tasks {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            setOf(TasksScopes.TASKS_READONLY)
        ).setSelectedAccount(account.account)

        return Tasks.Builder(
            NetHttpTransport(),
            GsonFactory(),
            credential
        )
            .setApplicationName("YourAppName") // Replace with your app name
            .build()
    }
}
