package org.edu.electricwife.app;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.model.ListConnectionsResponse;
import com.google.api.services.people.v1.model.Person;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, GoogleApiClient.OnConnectionFailedListener {

    private static final int RC_AUTH_CODE = 111;
    public static final String TAG = "GoogleOAuthClient";
    private static final String CONTACTS_SCOPE = "https://www.googleapis.com/auth/contacts.readonly";
    private static final HttpTransport HTTP_TRANSPORT = AndroidHttp.newCompatibleTransport();
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    public static List<Message> messages = new ArrayList<>();
    public static List<Message> news = new ArrayList<>();

    private static String lastCheckId = null;

    /* Client for accessing Google APIs */
    private static GoogleApiClient mGoogleApiClient;
    GoogleAccountCredential mCredential;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestServerAuthCode(getString(R.string.server_client_id))
                .requestEmail()
                .requestScopes(
                        new Scope(CONTACTS_SCOPE),
                        new Scope(GmailScopes.MAIL_GOOGLE_COM)
                )
                .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        findViewById(R.id.sign_in_button).setOnClickListener(this);

    }

    public void onSpeechButton(View view) {
        Intent intent = new Intent(this, SpeechToTextActivity.class);
        startActivity(intent);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.sign_in_button:
                signIn();
                break;
        }
    }


    public void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_AUTH_CODE);
    }


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // Could not connect to Google Play Services.  The user needs to select an account,
        // grant permissions or resolve an error in order to sign in. Refer to the javadoc for
        // ConnectionResult to see possible error codes.
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_AUTH_CODE) {

            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> task) {
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);

            String serverAuthCode = account.getServerAuthCode();

            Log.i(TAG, "serverAuthCode=" + serverAuthCode);
            updateUI(account);
        } catch (ApiException e) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
            updateUI(null);
        }
    }

    private void updateUI(@Nullable GoogleSignInAccount account) {
        if (account != null) {
//            new GetContactsTask(this).execute(account.getAccount());

            mCredential = GoogleAccountCredential.usingOAuth2(
                    getApplicationContext(), Collections.singletonList(GmailScopes.MAIL_GOOGLE_COM))
                    .setBackOff(new ExponentialBackOff());
            mCredential.setSelectedAccount(account.getAccount());
            new MakeRequestTask(mCredential).execute();
        }
    }

    protected void onConnectionsLoadFinished(@Nullable List<Person> connections) {
        if (connections == null) {
            Log.i(TAG, "getContacts:connections: null");
            return;
        }

        Log.d(TAG, "getContacts:connections: size=" + connections.size());

        // Get names of all connections
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < connections.size(); i++) {
            Person person = connections.get(i);
            if (person.getNames() != null && person.getNames().size() > 0) {
                msg.append(person.getNames().get(0).getDisplayName());

                if (i < connections.size() - 1) {
                    msg.append(",");
                }
            }
        }

        // Display names
        Log.i(TAG, msg.toString());
    }

    private class MakeRequestTask extends AsyncTask<Void, Void, List<Message>> {
        private com.google.api.services.gmail.Gmail mService = null;
        private Exception mLastError = null;

        MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.gmail.Gmail.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Gmail API Android Quickstart")
                    .build();
        }

        /**
         * Background task to call Gmail API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<Message> doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch a list of Gmail labels attached to the specified account.
         *
         * @return List of Strings labels.
         * @throws IOException
         */
        private List<Message> getDataFromApi() throws IOException {
            // Get the labels in the user's account.
            String user = "me";
            ListMessagesResponse list = mService.users()
                    .messages().list(user).execute();
            List<Message> prettyMessages = new ArrayList<>();
            for (Message message : list.getMessages().subList(0, 5)) {
                Message get = mService.users().messages().get(user, message.getId()).setFormat("full").execute();
                prettyMessages.add(get);
            }
            return prettyMessages;
        }


        @Override
        protected void onPreExecute() {
            Log.i(TAG, "");
        }

        @Override
        protected void onPostExecute(List<Message> output) {
            if (output == null || output.size() == 0) {
                Log.i(TAG, "No results returned.");
            } else {
                Log.i(TAG, TextUtils.join("\n", output));
                List<Message> news = new ArrayList<>();
                if (lastCheckId == null) {
                    lastCheckId = output.get(0).getId();
                }
                for (Message message : output) {
                    if (!message.getId().equals(lastCheckId)) {
                        news.add(message);
                    } else {
                        lastCheckId = output.get(0).getId();
                        break;
                    }
                }
                MainActivity.news = news;
                MainActivity.messages = output;
                Log.i(TAG, "You have " + news.size() + "messages");
            }
        }

        @Override
        protected void onCancelled() {
            if (mLastError != null) {
                Log.i(TAG, "The following error occurred:\n"
                        + mLastError.getMessage());
            } else {
                Log.i(TAG, "Request cancelled.");
            }
        }
    }

    private static class GetContactsTask extends AsyncTask<Account, Void, List<Person>> {

        private WeakReference<MainActivity> mActivityRef;

        public GetContactsTask(MainActivity activity) {
            mActivityRef = new WeakReference<>(activity);
        }

        @Override
        protected List<Person> doInBackground(Account... accounts) {
            if (mActivityRef.get() == null) {
                return null;
            }

            Context context = mActivityRef.get().getApplicationContext();
            try {
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        context,
                        Collections.singleton(CONTACTS_SCOPE));
                credential.setSelectedAccount(accounts[0]);

                PeopleService service = new PeopleService.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                        .setApplicationName("Google Sign In Quickstart")
                        .build();

                ListConnectionsResponse connectionsResponse = service
                        .people()
                        .connections()
                        .list("people/me")
                        .setPersonFields("names,emailAddresses")
                        .execute();

                return connectionsResponse.getConnections();

            } catch (Exception e) {
                Log.w(TAG, "getContacts:exception", e);
            }

            return null;
        }

        @Override
        protected void onPostExecute(List<Person> people) {
            super.onPostExecute(people);
            if (mActivityRef.get() != null) {
                mActivityRef.get().onConnectionsLoadFinished(people);
            }
        }
    }
}
