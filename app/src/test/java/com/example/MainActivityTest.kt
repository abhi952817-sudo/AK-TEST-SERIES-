package Com.ak.testseries

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import Com.ak.testseries.ui.LoginScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class MainActivityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testRealSignInFlowRenderAndInteraction() {
        var googleSignInClicked = false
        var magicLinkClicked = false

        composeTestRule.setContent {
            LoginScreen(
                onGoogleSignInClick = { googleSignInClicked = true },
                onSignInClick = { _, _ -> },
                onSignUpClick = { _, _, _ -> },
                onMagicLinkClick = { magicLinkClicked = true }
            )
        }

        // Verify Real Google Auth button is rendered and clickable
        composeTestRule.onNodeWithText("Continue with Google").performScrollTo().performClick()
        assertTrue("Google sign in should be triggered", googleSignInClicked)

        // Verify Magic Link button is rendered
        composeTestRule.onNodeWithText("Send Magic Link").performScrollTo().assertIsDisplayed()
    }
}
