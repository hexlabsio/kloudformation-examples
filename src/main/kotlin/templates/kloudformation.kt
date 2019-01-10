package templates

import io.kloudformation.KloudFormation
import io.kloudformation.StackBuilder
import io.kloudformation.function.plus
import io.kloudformation.property.Tag
import io.kloudformation.property.cloudfront.distribution.Origin
import io.kloudformation.property.elasticloadbalancingv2.listener.certificate


class Stack: StackBuilder {
    override fun KloudFormation.create() {
        // S3 Website with default settings
        s3Website {  }

        // S3 Website with some modifications ( errorDocument and bucket name)
        s3Website {
            s3Bucket {
                props { errorDocument = "404.html" }
                modify {
                    bucketName("MyBucket")
                }
            }
            s3BucketPolicy {
                props { bucketRef = +"" }
            }

        }

        // S3 Website without default bucket policy
        s3Website {
            s3BucketPolicy { remove() }
        }

        // S3 Website with klouds.io domain attached to a cloudfront distribution
        s3Website {
            domain("klouds.io")
        }

        // S3 Website with klouds.io domain attached to a cloudfront distribution with root object changed and tags added to distribution
        s3Website {
            domain("klouds.io"){
                props { this.defaultRootObject = +"another" + defaultRootObject }
                modify {
                    cloudFrontDistribution {
                        modify {
                            tags(listOf(Tag(+"A", +"B")))
                        }
                    }
                }
            }
        }

        // Run mvn package to produce template.yml (also checked in for reference)
    }
}