package templates.serverless

import io.kloudformation.KloudFormation
import io.kloudformation.Value
import io.kloudformation.function.plus
import io.kloudformation.model.iam.PolicyDocument
import io.kloudformation.model.iam.policyDocument
import io.kloudformation.property.aws.iam.role.policy
import io.kloudformation.resource.aws.iam.Role
import io.kloudformation.resource.aws.iam.role
import io.kloudformation.resource.aws.s3.Bucket
import io.kloudformation.resource.aws.s3.bucket
import io.kloudformation.resource.aws.servicediscovery.service
import templates.*

class Serverless(val serviceName: String, val stage: String,val bucket: Bucket, val lambdaRole: Role?): Module, ExtraParts {

    class FuncProps(val functionId: String,
                    val codeLocationKey: Value<String>,
                    val handler: Value<String>,
                    val runtime: Value<String>
    ): PartialProps<ServerlessFunction.FuncProps, Serverless> {
        override fun fullProps(extraProps: Serverless): ServerlessFunction.FuncProps {
            return ServerlessFunction.FuncProps(functionId,extraProps.serviceName,extraProps.stage,codeLocationKey,handler,runtime,extraProps.bucket,extraProps.lambdaRole)
        }
    }

    class Parts(
            val deploymentBucket: Modification<Bucket.Builder, Bucket, BucketProps> = modification(),
            val globalRole: OptionalModification<Role.Builder, Role, RoleProps> = optionalModification(absent = true)
    ){
        val serverlessFunction = SubModules<ServerlessFunction, ServerlessFunction.Parts, ServerlessFunction.Builder,FuncProps, ServerlessFunction.FuncProps, Serverless>(
                builder = { props -> ServerlessFunction.Builder(props)}
        )
        class BucketProps: Props
        data class RoleProps(var assumedRolePolicyDocument: PolicyDocument): Props
    }

    class Builder(
            val serviceName: String,
            val stage: String
    ): ModuleBuilder<Serverless, Parts>(Parts()){

        override fun KloudFormation.buildModule(): Parts.() -> Serverless = {
            val bucketResource = deploymentBucket(Serverless.Parts.BucketProps()){ props ->
                bucket {
                    modifyBuilder(props)
                }
            }
            val roleResource = roleFor(serviceName, stage, globalRole)
            val serverless = Serverless(serviceName, stage, bucketResource,roleResource)
            serverlessFunction.modules().forEach{
                it.module(serverless)()
            }
            serverless
        }

        companion object {
            fun KloudFormation.roleFor(serviceName: String, stage: String, roleMod: OptionalModification<Role.Builder, Role, Parts.RoleProps>): Role?{
                val defaultAssumeRole = policyDocument(version = io.kloudformation.model.iam.IamPolicyVersion.V2.version){
                    statement(action = io.kloudformation.model.iam.action("sts:AssumeRole")){
                        principal(io.kloudformation.model.iam.PrincipalType.SERVICE, kotlin.collections.listOf(+"lambda.amazonaws.com"))
                    }
                }
                val logResource = +"arn:" + io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsPartition + ":logs:" + io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsRegion + ":" + io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsAccountId + ":log-group:/aws/lambda/$serviceName-$stage-definition:*"
                return roleMod(Serverless.Parts.RoleProps(defaultAssumeRole)){ props ->
                    role(props.assumedRolePolicyDocument){
                        policies(kotlin.collections.listOf(
                                policy(
                                        policyName = +"$stage-$serviceName-lambda",
                                        policyDocument = io.kloudformation.model.iam.PolicyDocument(
                                                version = io.kloudformation.model.iam.IamPolicyVersion.V2.version,
                                                statement = kotlin.collections.listOf(
                                                        io.kloudformation.model.iam.PolicyStatement(
                                                                action = io.kloudformation.model.iam.action("logs:CreateLogStream"),
                                                                resource = io.kloudformation.model.iam.resource(logResource)
                                                        ),
                                                        io.kloudformation.model.iam.PolicyStatement(
                                                                action = io.kloudformation.model.iam.action("logs:PutLogEvents"),
                                                                resource = io.kloudformation.model.iam.resource(logResource + ":*")
                                                        )
                                                )
                                        )
                                )
                        ))
                        path("/")
                        roleName(+"$serviceName-$stage-" + io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsRegion + "-lambdaRole")
                        managedPolicyArns(kotlin.collections.listOf(
                                +"arn:" + io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsPartition + ":iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
                        ))
                        modifyBuilder(props)
                    }
                }
            }
        }
    }
}

fun KloudFormation.serverless(
        serviceName: String,
        stage: String = "dev",
        partBuilder: Serverless.Parts.()->Unit = {}
) = builder(Serverless.Builder(serviceName, stage), partBuilder)
