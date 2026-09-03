package com.example.gameworkbench.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("user_role_audit") public class UserRoleAudit {
 @TableId(type=IdType.AUTO) private Long id; private Long actorUserId; private Long targetUserId; private String operation; private String beforeRole; private String afterRole; private String beforeStatus; private String afterStatus; private LocalDateTime createdAt;
}
