<#macro content shape>
    /**
     * Returns a string representation of this object; useful for testing and
     * debugging.
     *
     * @return A string representation of this object.
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        <#if shape.members?has_content>
        <#list shape.members as member>
        <#local memberName = member.name>
        if (${member.getterMethodName}() != null) sb.append("${memberName}: ").append(${member.getterMethodName}())<#if member_has_next>.append(",")</#if>;
        </#list>
        </#if>
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;

        if (obj instanceof ${shape.shapeName} == false) return false;
        <#if shape.members?has_content>
        ${shape.shapeName} other = (${shape.shapeName})obj;
        <#list  shape.members as member>
        <#local memberName = member.name>
        if (other.${member.getterMethodName}() == null ^ this.${member.getterMethodName}() == null) return false;
        if (other.${member.getterMethodName}() != null && other.${member.getterMethodName}().equals(this.${member.getterMethodName}()) == false) return false;
        </#list>
        </#if>
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;

        <#if shape.members?has_content>
        <#list  shape.members as member>
        <#local memberName = member.name>
        hashCode = prime * hashCode + ((${member.getterMethodName}() == null) ? 0 : ${member.getterMethodName}().hashCode());
        </#list>
        </#if>
        return hashCode;
    }
</#macro>
