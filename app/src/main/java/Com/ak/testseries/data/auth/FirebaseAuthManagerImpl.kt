package Com.ak.testseries.data.auth

import android.content.Context
import android.content.Intent
import Com.ak.testseries.domain.auth.AuthManager
import Com.ak.testseries.domain.auth.AuthUser
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import Com.ak.testseries.R

class FirebaseAuthManagerImpl : AuthManager {

    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun signInWithGoogle(context: Context, intent: Intent?): Result<AuthUser> {
        return try {
            if (auth == null) {
                return Result.failure(Exception("Firebase Auth is not initialized"))
            }
            if (intent == null) {
                return Result.failure(Exception("Intent is null"))
            }

            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken ?: return Result.failure(Exception("ID Token is null"))

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth!!.signInWithCredential(credential).await()
            val firebaseUser = authResult.user

            if (firebaseUser != null) {
                val authUser = AuthUser(
                    uid = firebaseUser.uid,
                    name = firebaseUser.displayName ?: "User",
                    email = firebaseUser.email ?: ""
                )
                Result.success(authUser)
            } else {
                Result.failure(Exception("Firebase User is null after sign in"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> {
        return try {
            if (auth == null) return Result.failure(Exception("Firebase Auth not initialized"))
            val result = auth!!.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(Exception("User is null"))
            Result.success(AuthUser(user.uid, user.displayName ?: "User", user.email ?: ""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signUpWithEmail(name: String, email: String, password: String): Result<AuthUser> {
        return try {
            if (auth == null) return Result.failure(Exception("Firebase Auth not initialized"))
            val result = auth!!.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(Exception("User is null"))
            
            // Update profile with name
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()
            user.updateProfile(profileUpdates).await()
            
            Result.success(AuthUser(user.uid, name, user.email ?: ""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    override suspend fun sendSignInLinkToEmail(email: String, context: Context): Result<Unit> {
        return try {
            if (auth == null) return Result.failure(Exception("Firebase Auth not initialized"))
            
            val actionCodeSettings = com.google.firebase.auth.ActionCodeSettings.newBuilder()
                .setUrl("https://aktestseries.app/login") // Must match deep link in Manifest
                .setHandleCodeInApp(true)
                .setAndroidPackageName(context.packageName, true, "12") // Replace with dynamic min version if needed
                .build()
                
            auth!!.sendSignInLinkToEmail(email, actionCodeSettings).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithEmailLink(email: String, link: String): Result<AuthUser> {
        return try {
            if (auth == null) return Result.failure(Exception("Firebase Auth not initialized"))
            if (!auth!!.isSignInWithEmailLink(link)) {
                return Result.failure(Exception("Invalid Magic Link"))
            }
            val result = auth!!.signInWithEmailLink(email, link).await()
            val user = result.user ?: return Result.failure(Exception("User is null"))
            Result.success(AuthUser(user.uid, user.displayName ?: "User", user.email ?: ""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getSignedInUser(): AuthUser? {
        val user = auth?.currentUser
        return if (user != null) {
            AuthUser(
                uid = user.uid,
                name = user.displayName ?: "User",
                email = user.email ?: ""
            )
        } else {
            null
        }
    }

    override suspend fun signOut() {
        auth?.signOut()
    }

    override fun getSignInIntent(context: Context): Intent? {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        val clientId = if (resId != 0) context.getString(resId) else ""
        
        if (clientId.isEmpty() || clientId == "your-web-client-id.apps.googleusercontent.com") {
            android.widget.Toast.makeText(context, "Google Sign-In is not configured correctly. Please update google-services.json.", android.widget.Toast.LENGTH_LONG).show()
            return null
        }
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()
        
        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        return googleSignInClient.signInIntent
    }
}
