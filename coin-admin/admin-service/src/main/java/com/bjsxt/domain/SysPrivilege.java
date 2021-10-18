package com.bjsxt.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
    * 权限配置
    */
@ApiModel(value="权限配置")
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "sys_privilege")
public class SysPrivilege {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.INPUT)
    @ApiModelProperty(value="主键")
    private Long id;

    /**
     * 所属菜单Id
     */
    @TableField(value = "menu_id")
    @ApiModelProperty(value="所属菜单Id")
    private Long menuId;

    /**
     * 功能点名称
     */
    @TableField(value = "`name`")
    @ApiModelProperty(value="功能点名称")
    private String name;

    /**
     * 功能描述
     */
    @TableField(value = "description")
    @ApiModelProperty(value="功能描述")
    private String description;

    @TableField(value = "url")
    @ApiModelProperty(value="")
    private String url;

    @TableField(value = "`method`")
    @ApiModelProperty(value="")
    private String method;

    /**
     * 创建人
     */
    @TableField(value = "create_by")
    @ApiModelProperty(value="创建人")
    private Long createBy;

    /**
     * 修改人
     */
    @TableField(value = "modify_by")
    @ApiModelProperty(value="修改人")
    private Long modifyBy;

    /**
     * 创建时间
     */
    @TableField(value = "created")
    @ApiModelProperty(value="创建时间")
    private Date created;

    /**
     * 修改时间
     */
    @TableField(value = "last_update_time")
    @ApiModelProperty(value="修改时间")
    private Date lastUpdateTime;

    public static final String COL_ID = "id";

    public static final String COL_MENU_ID = "menu_id";

    public static final String COL_NAME = "name";

    public static final String COL_DESCRIPTION = "description";

    public static final String COL_URL = "url";

    public static final String COL_METHOD = "method";

    public static final String COL_CREATE_BY = "create_by";

    public static final String COL_MODIFY_BY = "modify_by";

    public static final String COL_CREATED = "created";

    public static final String COL_LAST_UPDATE_TIME = "last_update_time";
}