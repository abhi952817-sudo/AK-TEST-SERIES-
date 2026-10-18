package Com.ak.testseries

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelProvider
import Com.ak.testseries.viewmodel.NEETViewModel
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewModelFactoryTest {
    @Test
    fun testFactory() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        val vm = factory.create(NEETViewModel::class.java)
        println("ViewModel created: $vm")
    }
}
