${fileHeader}
package ${metadata.fullModelPackageName};

import javax.annotation.Generated;

import software.amazon.awssdk.core.AmazonWebServiceRequest;
import software.amazon.awssdk.core.Request;
import ${metadata.fullTransformPackageName}.${shape.shapeName}Marshaller;

/**
 * <#if shape.documentation?has_content>${shape.documentation}</#if>
 */
@Generated("software.amazon.awssdk:aws-java-sdk-code-generator")
public class ${shape.shapeName} extends AmazonWebServiceRequest
        implements Cloneable, DryRunSupportedRequest<${shape.shapeName}> {

    <@VariableDeclarationMacro.content shape/>

    <#if shape.additionalConstructors?has_content>
        <@ConstructorDefinitionMacro.content shape/>
    </#if>

    <@MethodDefinitionMacro.content customConfig shape shape.shapeName/>

    /**
     * This method is intended for internal use only.
     * Returns the marshaled request configured with additional parameters to
     * enable operation dry-run.
     */
    @Override
    public Request<${shape.shapeName}> getDryRunRequest() {
        Request<${shape.shapeName}> request = new ${shape.shapeName}Marshaller().marshall(this);
        request.addParameter("DryRun", Boolean.toString(true));
        return request;
    }

    <@OverrideMethodsMacro.content shape />

    @Override
    public ${shape.shapeName} clone() {
        return (${shape.shapeName}) super.clone();
    }
}
