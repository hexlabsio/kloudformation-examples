package templates

import io.kloudformation.KloudFormation
import io.kloudformation.StackBuilder
import io.kloudformation.Value
import io.kloudformation.function.plus
import io.kloudformation.model.ParameterType
import io.kloudformation.property.Tag
import io.kloudformation.property.aws.ec2.securitygroup.Ingress
import io.kloudformation.property.aws.lambda.function.code
import io.kloudformation.resource.aws.ec2.securityGroup
import templates.serverless.Serverless
import templates.serverless.ServerlessFunction
import templates.serverless.serverless
import templates.website.S3Website
import templates.website.s3Website


class Stack: StackBuilder {
    fun KloudFormation.s3WebsiteExamples(){
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
            s3Distribution(S3Website.Parts.DistributionProps(+"klouds.io")){

            }
        }

        val cert = "certFromSomewhereElse"
        // S3 Website with klouds.io domain attached to a cloudfront distribution with root object changed and tags added to distribution
        s3Website {
            s3Distribution(S3Website.Parts.DistributionProps(+"klouds.io")){
                props { this.defaultRootObject = +"another" + defaultRootObject }
                modify {
                    bucketCertificate { remove() }
                    cloudFrontDistribution {
                        props { config = config.copy(viewerCertificate = config.viewerCertificate?.copy(acmCertificateArn = +cert)) }
                        modify {
                            tags(listOf(Tag(+"A", +"B")))
                        }
                    }
                }
            }
        }

    }
    override fun KloudFormation.create() {
        //s3WebsiteExamples()
        val vpc = parameter<String>("VPCId")
        val subnets = parameter<List<Value<String>>>("Subnets", "List<AWS::EC2::Subnet::Id>")
        val securityGroup = securityGroup(+"SG for lambda in VPC"){
            groupName("UniqueGroupName")
            vpcId(vpc.ref())
            securityGroupIngress(listOf(
                    Ingress(
                            ipProtocol = +"tcp",
                            fromPort = Value.Of(443),
                            toPort = Value.Of(443),
                            cidrIp = +"0.0.0.0/0"
                    )
            ))
        }
        serverless("myService","nonprod", Serverless.PrivateConfig(+listOf(securityGroup.ref()), subnets.ref())) {
            globalRole {
                modify { path("/anotherPath/") }
            }
            // Lambda that uses global role that runs in the public domain
            serverlessFunction(Serverless.FuncProps(
                    codeLocationKey = parameter<String>("CodeLocationKey").ref(),
                    functionId = "myFunction",
                    handler = +"org.http4k.serverless.lambda.LambdaFunction::handle",
                    runtime = +"java8",
                    privateConfig = Serverless.NoPrivateConfig)
            )

            //Lambda that uses modified role, updates properties and runs in private subnets triggered by a get request
            serverlessFunction(Serverless.FuncProps(
                    codeLocationKey = parameter<String>("CodeLocationKey2").ref(),
                    functionId = "myFunction2",
                    handler = +"org.http4k.serverless.lambda.LambdaFunction::handle",
                    runtime = +"java8")
            ){
                modify {
                    http(ServerlessFunction.HttpProps(
                            method = "GET"
                    ))
                    lambdaFunction {
                        modify {
                            timeout(20)
                            environment{
                                variables(mapOf(
                                        "HTTP4K_BOOTSTRAP_CLASS" to +"handler"
                                ))
                            }
                        }
                    }
                }
            }
        }
        // Run mvn package to produce template.yml (also checked in for reference)
    }
}