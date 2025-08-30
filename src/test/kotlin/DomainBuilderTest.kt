import dev.babies.application.os.host.DomainBuilder
import dev.babies.application.os.host.EmptyDomainException
import dev.babies.application.os.host.MalformedDomainException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe

class DomainBuilderTest : FunSpec() {
    init {
        test("DomainBuilder") {
            val domain = "sub.example.com"
            val builder = DomainBuilder(domain)
            val result = builder.toString()

            result shouldBeEqual domain
        }

        test("Empty Domain exception") {
            val domain = ""
            val e = shouldThrow<EmptyDomainException> { DomainBuilder(domain) }
            e.message shouldBe "Domain must not be empty"
        }

        test("Malformed Domain exception") {
            val domain = "invalid. domain"
            shouldThrow<MalformedDomainException> { DomainBuilder(domain) }
        }

        test("With suffix only once") {
            val base = "myapplication"
            val domain = "$base.hostingprovider.com"
            val suffix = "hostingprovider.com"
            val builder = DomainBuilder(domain)
            val result = builder.buildAsSubdomain(skipIfSuffixAlreadyPresent = true, suffix = suffix)

            result shouldBeEqual "$base.$suffix"
        }

        test("With suffix multiple times") {
            val domain = "myapplication.hostingprovider.com"
            val suffix = "hostingprovider.com"
            val builder = DomainBuilder(domain)
            val result = builder.buildAsSubdomain(skipIfSuffixAlreadyPresent = false, suffix = suffix)
            result shouldBeEqual "$domain.$suffix"
        }

        test("Remove suffix") {
            val domain = "myapplication.hostingprovider.com"
            val suffix = "hostingprovider.com"
            val builder = DomainBuilder(domain)
            builder.dropSuffix(suffix)
            builder.toString() shouldBeEqual "myapplication"
        }

        test("Chain") {
            var builder = DomainBuilder("hostingprovider.co.uk")
                .addSubdomain("myapplication")
                .addSubdomain("sub")
            "sub.myapplication.hostingprovider.co.uk" shouldBe builder.toString()
        }
    }
}