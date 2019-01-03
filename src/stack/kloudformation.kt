import io.kloudformation.KloudFormation
import io.kloudformation.StackBuilder


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
        }

        // S3 Website without default bucket policy
        s3Website {
            s3BucketPolicy { remove() }
        }

        // Run mvn package to produce template.yml (also checked in for reference)
    }
}