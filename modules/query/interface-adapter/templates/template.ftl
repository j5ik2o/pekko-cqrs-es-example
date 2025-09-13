package io.github.j5ik2o.pcqrses.query.interfaceAdapter.dao
// このファイルは自動生成ファイルなので直接編集しないでください。
import slick.lifted.PrimaryKey
import slick.lifted.ProvenShape

<#assign softDelete=false>
trait ${className}Component extends SlickDaoSupport with ${className}Extensions {
  import profile.api._

  final case class ${className}Record(
<#list primaryKeys as primaryKey>
      ${primaryKey.propertyName}: ${primaryKey.propertyTypeName}<#if primaryKey_has_next>,</#if></#list><#if primaryKeys?has_content>,</#if>
<#list columns as column>
    <#if column.columnName == "status">
        <#assign softDelete=true>
    </#if>
    <#if column.nullable>      ${column.propertyName}: Option[${column.propertyTypeName}]<#if column_has_next>,</#if>
    <#else>      ${column.propertyName}: ${column.propertyTypeName}<#if column_has_next>,</#if>
    </#if>
</#list>
  ) extends <#if softDelete == false>Record<#else>SoftDeletableRecord</#if>

  final case class ${className}(tag: Tag) extends TableBase[${className}Record](tag, "${tableName}")<#if softDelete == true> with SoftDeletableTableSupport[${className}Record]</#if> {
<#list primaryKeys as primaryKey>
    def ${primaryKey.propertyName}: Rep[${primaryKey.propertyTypeName}] = column[${primaryKey.propertyTypeName}]("${primaryKey.columnName}")
</#list>
<#list columns as column>
    <#if column.nullable>
    def ${column.propertyName}: Rep[Option[${column.propertyTypeName}]] = column[Option[${column.propertyTypeName}]]("${column.columnName}")
    <#else>
    def ${column.propertyName}: Rep[${column.propertyTypeName}] = column[${column.propertyTypeName}]("${column.columnName}")
    </#if>
</#list>

    def pk: PrimaryKey = primaryKey("pk", (<#list primaryKeys as primaryKey>${primaryKey.propertyName}<#if primaryKey_has_next>, </#if></#list>))

    override def * : ProvenShape[${className}Record] = (<#list primaryKeys as primaryKey>${primaryKey.propertyName}<#if primaryKey_has_next>, </#if></#list><#if primaryKeys?has_content>, </#if><#list columns as column>${column.propertyName}<#if column_has_next>, </#if></#list>) <> (${className}Record.apply, ${className}Record.unapply)
  }

  object ${className}Dao extends TableQuery(${className}.apply) with ${className}DaoExtensions

}
