package templates.serverless

import io.kloudformation.KloudFormation
import io.kloudformation.Value
import io.kloudformation.property.aws.lambda.function.Code
import io.kloudformation.resource.aws.iam.Role
import io.kloudformation.resource.aws.lambda.Function
import io.kloudformation.resource.aws.lambda.function
import io.kloudformation.resource.aws.logs.LogGroup
import io.kloudformation.resource.aws.logs.logGroup
import io.kloudformation.resource.aws.s3.Bucket
import templates.*

class ServerlessFunction(val logGroup: LogGroup, val role: Role?, val function: Function) : Module {

    class FuncProps(
            val functionId: String,
            val serviceName: String,
            val stage: String,
            val codeLocationKey: Value<String>,
            val handler: Value<String>,
            val runtime: Value<String>,
            val globalBucket: Bucket,
            val globalRole: Role? = null,
            val privateConfig: Serverless.PrivateConfig? = null
    ): Props

    class HttpProps(
            val cors: HttpModule.CorsConfig? = null,
            val vpcEndpoint: Value<String>? = null
    ): PartialProps<HttpModule.HttpFullProps, Serverless.Globals> {
        override fun fullProps(extraProps: Serverless.Globals): HttpModule.HttpFullProps {
            return HttpModule.HttpFullProps(extraProps.serviceName, extraProps.stage, cors, vpcEndpoint)
        }
    }

    class Parts(
            val lambdaLogGroup: Modification<LogGroup.Builder, LogGroup, LogGroupProps> = modification(),
            val lambdaRole: OptionalModification<Role.Builder, Role, Serverless.Parts.RoleProps> = optionalModification(absent = true),
            val lambdaFunction: Modification<Function.Builder, Function, LambdaProps> = modification()
    ){
        data class LogGroupProps(var name: Value<String>): Props
        data class LambdaProps(var code: Code, var handler: Value<String>, var role: Value<String>, var runtime: Value<String>): Props
        val http = SubModules<HttpModule, HttpModule.Parts, HttpModule.Builder, HttpProps, HttpModule.HttpFullProps, Serverless.Globals>(
                builder = { props -> HttpModule.Builder(props)}
        )
    }

    class Builder(
            val builderProps: FuncProps
    ): ModuleBuilder<ServerlessFunction, Parts>(Parts()){

        override fun KloudFormation.buildModule(): Parts.() -> ServerlessFunction = {
            val logGroupResource = lambdaLogGroup(Parts.LogGroupProps(name = +"/aws/lambda/${builderProps.serviceName}-${builderProps.stage}-${builderProps.functionId}")) { props ->
                logGroup {
                    logGroupName(props.name)
                    modifyBuilder(props)
                }
            }
            val code = Code(
                    s3Bucket = builderProps.globalBucket.ref(),
                    s3Key = builderProps.codeLocationKey
            )
            if(builderProps.globalRole == null) lambdaRole.keep()
            val roleResource = Serverless.Builder.run { roleFor(builderProps.serviceName, builderProps.stage, lambdaRole) } ?: builderProps.globalRole
            val lambdaResource = lambdaFunction(Parts.LambdaProps(code,builderProps.handler,roleResource?.ref() ?: +"",builderProps.runtime)) { props ->
                function(props.code, props.handler, props.role, props.runtime,
                        dependsOn = kotlin.collections.listOfNotNull(logGroupResource.logicalName, roleResource?.logicalName)){
                    builderProps.privateConfig?.let { config ->
                        if(config.securityGroups != null && config.subnetIds != null){
                            vpcConfig(config.securityGroups, config.subnetIds)
                        }
                    }
                    modifyBuilder(props)
                }
            }
            http.modules().forEach{
                it.module(Serverless.Globals(builderProps.serviceName, builderProps.stage))()
            }
            ServerlessFunction(logGroupResource, roleResource, lambdaResource)
        }
    }
}