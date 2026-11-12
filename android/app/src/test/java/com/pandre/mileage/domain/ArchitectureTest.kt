package com.pandre.mileage.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureTest {
    @Test
    fun domainAndPresentationRespectDependencyDirection() {
        val root = File("src/main/java/com/pandre/mileage")
        val domain =
            File("../domain/src/main/kotlin/com/pandre/mileage/domain")
                .walkTopDown()
                .filter { it.extension == "kt" }
                .toList()
        val ui = File(root, "ui").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("Domain source scope must not be empty", domain.isNotEmpty())
        assertTrue("Presentation source scope must not be empty", ui.isNotEmpty())
        domain.forEach { file ->
            val imports = file.readLines().filter { it.startsWith("import ") }
            assertFalse(
                file.name,
                imports.any {
                    it.contains("android.") ||
                        it.contains("androidx.") ||
                        it.contains("retrofit2.") ||
                        it.contains("com.google.firebase") ||
                        it.contains("mileage.data") ||
                        it.contains("mileage.ui")
                },
            )
        }
        val data =
            File("../data/src/main/java/com/pandre/mileage/data")
                .walkTopDown()
                .filter { it.extension == "kt" }
                .toList()
        assertTrue("Data source scope must not be empty", data.isNotEmpty())
        data.forEach { file ->
            assertFalse(
                file.name,
                file.readLines().any {
                    it.startsWith("import ") &&
                        (it.contains("mileage.ui") ||
                            it.contains("mileage.platform") ||
                            it.contains("mileage.BuildConfig"))
                },
            )
        }
        ui.forEach { file ->
            assertFalse(
                file.name,
                file.readLines().any {
                    it.startsWith("import ") &&
                        (it.contains("domain.MileageRepository") ||
                            it.contains("mileage.data") ||
                            it.contains("retrofit2.") ||
                            it.contains("androidx.room") ||
                            it.contains("com.google.firebase"))
                },
            )
        }
    }
}
