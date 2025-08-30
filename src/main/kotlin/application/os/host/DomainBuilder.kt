package dev.babies.application.os.host

class DomainBuilder {
    val domains = mutableListOf<String>()

    constructor(domainBuilder: DomainBuilder): this(domainBuilder.toString())

    constructor(domain: String) {
        if (domain.isBlank()) throw EmptyDomainException()
        if (".." in domain) throw MalformedDomainException("Parts cannot be empty")

        val parts = domain.split(".")

        parts.firstOrNull { part -> part != nameToDomain(part) }?.let {
            throw MalformedDomainException("Domain parts must be lowercase and contain only letters, numbers, and hyphens, found $it")
        }
        domains.addAll(parts)
    }

    fun buildAsSubdomain(skipIfSuffixAlreadyPresent: Boolean = true, suffix: String): String {
        val currentDomain = domains.joinToString(".")
        if (skipIfSuffixAlreadyPresent && currentDomain.endsWith(".$suffix")) return currentDomain
        return "$currentDomain.$suffix"
    }

    fun dropSuffix(suffix: String) {
        val suffixDomains = suffix.split(".").toMutableList()
        suffixDomains.reversed().forEach { domain ->
            if (domains.last() == domain) domains.removeLast() else throw DomainDoesNotContainSuffixException(suffix)
        }
    }

    fun addSubdomain(subdomain: String): DomainBuilder {
        domains.add(0, subdomain)
        return this
    }

    override fun toString(): String {
        return domains.joinToString(".")
    }

    companion object {
        fun nameToDomain(name: String): String {
            return name.lowercase()
                .replace(" ", "-")
        }
    }
}

open class DomainException(message: String) : Exception(message)
open class MalformedDomainException(message: String) : DomainException(message)
class EmptyDomainException : MalformedDomainException("Domain must not be empty")
class DomainDoesNotContainSuffixException(suffix: String) : DomainException("Domain does not contain suffix $suffix")