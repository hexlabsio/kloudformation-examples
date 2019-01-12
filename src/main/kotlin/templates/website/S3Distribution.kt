package templates.website

import io.kloudformation.KloudFormation
import io.kloudformation.Value
import io.kloudformation.function.plus
import io.kloudformation.property.aws.cloudfront.distribution.DistributionConfig
import io.kloudformation.resource.aws.certificatemanager.Certificate
import io.kloudformation.resource.aws.certificatemanager.certificate
import io.kloudformation.resource.aws.cloudfront.Distribution
import io.kloudformation.resource.aws.cloudfront.distribution
import templates.*

enum class CertificationValidationMethod{ EMAIL, DNS }
enum class SslSupportMethod(val value: String){ SNI("sni-only"), VIP("vip") }
enum class HttpMethod(val value: String){ HTTP1_1("http1.1"), HTTP2("http2") }
enum class CloudfrontPriceClass(val value: String){ _100("PriceClass_100"), _200("PriceClass_200"), ALL("PriceClass_ALL") }

class S3Distribution: Module {
    class S3DistProps(var domain: Value<String>, var bucketRef: Value<String>, var defaultRootObject: Value<String>, var httpMethod: HttpMethod, var sslSupportMethod: SslSupportMethod, var priceClass: CloudfrontPriceClass): Props

    class Parts {
        val bucketCertificate: OptionalModification<Certificate.Builder, Certificate, CertificateProps> = optionalModification()
        val cloudFrontDistribution: OptionalModification<Distribution.Builder, Distribution, DistributionProps> = optionalModification()
    }

    data class CertificateProps(var domain: Value<String>, var validationMethod: CertificationValidationMethod = CertificationValidationMethod.DNS): Props
    data class DistributionProps(var config: DistributionConfig): Props

    class Builder(val props: S3DistProps): ModuleBuilder<S3Distribution, Parts>(Parts()) {

        override fun KloudFormation.buildModule(): Parts.() -> S3Distribution = {
            val cert = bucketCertificate(CertificateProps(props.domain)) { props ->
                certificate(props.domain) {
                    subjectAlternativeNames(kotlin.collections.listOf(props.domain))
                    domainValidationOptions(kotlin.collections.listOf(io.kloudformation.property.aws.certificatemanager.certificate.DomainValidationOption(
                            domainName = props.domain,
                            validationDomain = props.domain
                    )))
                    validationMethod(props.validationMethod.toString())
                    modifyBuilder(props)
                }
            }
            val origin = io.kloudformation.property.aws.cloudfront.distribution.Origin(
                    id = +"s3Origin",
                    domainName = props.bucketRef + +".s3-website-" + io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsRegion + +".amazonaws.com",
                    customOriginConfig = io.kloudformation.property.aws.cloudfront.distribution.CustomOriginConfig(
                            originProtocolPolicy = +"http-only"
                    )
            )
            val distributionProps = DistributionProps(DistributionConfig(
                    origins = kotlin.collections.listOf(origin),
                    enabled = +true,
                    aliases = +kotlin.collections.listOf(+"www." + props.domain, props.domain),
                    defaultCacheBehavior = io.kloudformation.property.aws.cloudfront.distribution.DefaultCacheBehavior(
                            allowedMethods = +kotlin.collections.listOf(+"GET", +"HEAD", +"OPTIONS"),
                            forwardedValues = io.kloudformation.property.aws.cloudfront.distribution.ForwardedValues(queryString = +true),
                            targetOriginId = origin.id,
                            viewerProtocolPolicy = +"allow-all"
                    ),
                    defaultRootObject = props.defaultRootObject,
                    priceClass = +props.priceClass.value,
                    httpVersion = +props.httpMethod.value,
                    viewerCertificate = io.kloudformation.property.aws.cloudfront.distribution.ViewerCertificate(acmCertificateArn = cert?.ref(), sslSupportMethod = +props.sslSupportMethod.value)
            ))
            val cfDistribution = cloudFrontDistribution(distributionProps){ props ->
                distribution(props.config) {

                    modifyBuilder(props)
                }
            }
            S3Distribution()
        }
    }
}