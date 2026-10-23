package com.ppdv.api.system.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.constant.Constants;
import com.ppdv.api.common.core.redis.RedisCache;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.security.LoginUser;
import com.ppdv.api.system.domain.SysDept;
import com.ppdv.api.system.domain.vo.OnlineUser;
import com.ppdv.api.system.repository.SysDeptRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 在线用户：读取 Redis 中的登录会话，支持查询与强制下线。
 */
@RestController
@RequestMapping("/monitor/online")
public class SysOnlineController extends BaseController {

    private final RedisCache redisCache;
    private final SysDeptRepository deptRepository;

    public SysOnlineController(RedisCache redisCache, SysDeptRepository deptRepository) {
        this.redisCache = redisCache;
        this.deptRepository = deptRepository;
    }

    @PreAuthorize("@ss.hasPermi('monitor:online:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(value = "userName", required = false) String userName,
                              @RequestParam(value = "ipaddr", required = false) String ipaddr) {
        List<OnlineUser> rows = new ArrayList<>();
        Collection<Object> keys = redisCache.keys(Constants.LOGIN_TOKEN_KEY + "*");
        if (keys != null) {
            for (Object keyObj : keys) {
                Object value = redisCache.get(String.valueOf(keyObj));
                if (!(value instanceof LoginUser loginUser)) {
                    continue;
                }
                if (StringUtils.hasText(userName)
                        && !contains(loginUser.getUserName(), userName)
                        && !contains(loginUser.getNickName(), userName)) {
                    continue;
                }
                if (StringUtils.hasText(ipaddr) && !contains(loginUser.getIpaddr(), ipaddr)) {
                    continue;
                }
                rows.add(toOnlineUser(loginUser));
            }
        }
        rows.sort(Comparator.comparingLong(OnlineUser::getLoginTime).reversed());
        return new TableDataInfo(rows, rows.size());
    }

    @PreAuthorize("@ss.hasPermi('monitor:online:forceLogout')")
    @DeleteMapping("/{tokenId}")
    public AjaxResult forceLogout(@PathVariable String tokenId) {
        redisCache.delete(Constants.LOGIN_TOKEN_KEY + tokenId);
        return AjaxResult.success();
    }

    private OnlineUser toOnlineUser(LoginUser loginUser) {
        OnlineUser online = new OnlineUser();
        online.setTokenId(loginUser.getToken());
        online.setUserName(loginUser.getUserName());
        online.setDeptName(resolveDeptName(loginUser.getDeptId()));
        online.setIpaddr(loginUser.getIpaddr());
        online.setLoginLocation("");
        online.setOs(loginUser.getOs());
        online.setBrowser(loginUser.getBrowser());
        online.setLoginTime(loginUser.getLoginTime());
        return online;
    }

    private String resolveDeptName(Long deptId) {
        if (deptId == null) {
            return "";
        }
        return deptRepository.findById(deptId).map(SysDept::getDeptName).orElse("");
    }

    private boolean contains(String value, String filter) {
        return value != null && value.contains(filter);
    }
}
