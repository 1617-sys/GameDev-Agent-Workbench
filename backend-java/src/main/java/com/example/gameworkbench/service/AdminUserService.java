package com.example.gameworkbench.service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page; import com.example.gameworkbench.dto.admin.UpdateUserAccessRequest; import com.example.gameworkbench.entity.UserRoleAudit; import com.example.gameworkbench.vo.admin.AdminUserVO; import java.util.List;
public interface AdminUserService { Page<AdminUserVO> list(long actorId,int pageNum,int pageSize,String username,String role,String status); AdminUserVO update(long actorId,long targetId,UpdateUserAccessRequest request); List<UserRoleAudit> audits(long targetId); }
