package com.bjsxt.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjsxt.config.IdAutoConfiguration;
import com.bjsxt.domain.UserAuthAuditRecord;
import com.bjsxt.dto.UserDto;
import com.bjsxt.geetest.GeetestLib;
import com.bjsxt.mappers.UserDtoMapper;
import com.bjsxt.model.RegisterParam;
import com.bjsxt.model.UserAuthForm;
import com.bjsxt.service.UserAuthAuditRecordService;
import com.bjsxt.service.UserAuthInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bjsxt.domain.User;
import com.bjsxt.mapper.UserMapper;
import com.bjsxt.service.UserService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private UserAuthAuditRecordService userAuthAuditRecordService;

    @Autowired
    private UserAuthInfoService userAuthInfoService;

    @Autowired
    private GeetestLib geetestLib;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 条件分页查询会员的列表
     *
     * @param page     分页参数
     * @param mobile   会员的手机号
     * @param userId   会员的ID
     * @param userName 会员的名称
     * @param realName 会员的真实名称
     * @param status   会员的状态
     * @return
     */
    @Override
    public Page<User> findByPage(Page<User> page, String mobile, Long userId, String userName, String realName, Integer status, Integer reviewStatus) {
        return page(page,
                new LambdaQueryWrapper<User>()
                        .like(!StringUtils.isEmpty(mobile), User::getMobile, mobile)
                        .like(!StringUtils.isEmpty(userName), User::getUsername, userName)
                        .like(!StringUtils.isEmpty(realName), User::getRealName, realName)
                        .eq(userId != null, User::getId, userId)
                        .eq(status != null, User::getStatus, status)
                        .eq(reviewStatus != null, User::getReviewsStatus, reviewStatus)
        );
    }

    /**
     * 通过用户的Id 查询该用户邀请的人员
     *
     * @param page   分页参数
     * @param userId 用户的Id
     * @return
     */
    @Override
    public Page<User> findDirectInvitePage(Page<User> page, Long userId) {
        return page(page, new LambdaQueryWrapper<User>().eq(User::getDirectInviteid, userId));
    }

    /**
     * 修改用户的审核状态
     *
     * @param id
     * @param authStatus
     * @param authCode
     */
    @Override
    @Transactional
    public void updateUserAuthStatus(Long id, Byte authStatus, Long authCode, String remark) {
        log.info("开始修改用户的审核状态,当前用户{},用户的审核状态{},图片的唯一code{}", id, authStatus, authCode);
        User user = getById(id);
        if (user != null) {
//            user.setAuthStatus(authStatus); // 认证的状态
            user.setReviewsStatus(authStatus.intValue()); // 审核的状态
            updateById(user); // 修改用户的状态
        }
        UserAuthAuditRecord userAuthAuditRecord = new UserAuthAuditRecord();
        userAuthAuditRecord.setUserId(id);
        userAuthAuditRecord.setStatus(authStatus);
        userAuthAuditRecord.setAuthCode(authCode);

        String usrStr = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        userAuthAuditRecord.setAuditUserId(Long.valueOf(usrStr)); // 审核人的ID
        userAuthAuditRecord.setAuditUserName("---------------------------");// 审核人的名称 --> 远程调用admin-service ,没有事务
        userAuthAuditRecord.setRemark(remark);

        userAuthAuditRecordService.save(userAuthAuditRecord);
    }

    /**
     * 获取该用户邀请的用户列表
     *
     * @param userId 用户的Id
     * @return
     */
    @Override
    public List<User> getUserInvites(Long userId) {
        List<User> list = list(new LambdaQueryWrapper<User>().eq(User::getDirectInviteid, userId));
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        list.forEach(user -> {
            user.setPaypassword("*********");
            user.setPassword("********");
            user.setAccessKeyId("*********");
            user.setAccessKeySecret("*********");
        });
        return list;
    }

    /**
     * 通过用户的信息查询用户
     *
     * @param ids      用户的批量查询,用在我们给别人远程调用时批量获取用户的数据
     * @return
     */
    @Override
    public List<UserDto> getBasicUsers(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        List<User> list = list(new LambdaQueryWrapper<User>().in(User::getId, ids));
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        // 将user->userDto
        List<UserDto> userDtos = UserDtoMapper.INSTANCE.convert2Dto(list);
        return userDtos;
    }

    /**
     * 用户的注册
     *
     * @param registerParam 注册的表单参数
     * @return
     */
    @Override
    public boolean register(RegisterParam registerParam) {
        log.info("用户开始注册{}", JSON.toJSONString(registerParam, true));
        String mobile = registerParam.getMobile();
        String email = registerParam.getEmail();
        // 1 简单的校验
        if (StringUtils.isEmpty(email) && StringUtils.isEmpty(mobile)) {
            throw new IllegalArgumentException("手机号或邮箱不能同时为空");
        }
        // 2 查询校验
        int count = count(new LambdaQueryWrapper<User>()
                .eq(!StringUtils.isEmpty(email), User::getEmail, email)
                .eq(!StringUtils.isEmpty(mobile), User::getMobile, mobile)
        );
        if (count > 0) {
            throw new IllegalArgumentException("手机号或邮箱已经被注册");
        }

        registerParam.check(geetestLib, redisTemplate); // 进行极验的校验
        User user = getUser(registerParam); // 构建一个新的用户
        return save(user);
    }

    private User getUser(RegisterParam registerParam) {
        User user = new User();
        user.setCountryCode(registerParam.getCountryCode());
        user.setEmail(registerParam.getEmail());
        user.setMobile(registerParam.getMobile());
        String encodePwd = new BCryptPasswordEncoder().encode(registerParam.getPassword());
        user.setPassword(encodePwd);
        user.setPaypassSetting(false);
        user.setStatus((byte) 1);
        user.setType((byte) 1);
        user.setAuthStatus((byte) 0);
        user.setLogins(0);
        user.setInviteCode(RandomUtil.randomString(6)); // 用户的邀请码
        if (!StringUtils.isEmpty(registerParam.getInvitionCode())) {
            User userPre = getOne(new LambdaQueryWrapper<User>().eq(User::getInviteCode, registerParam.getInvitionCode()));
            if (userPre != null) {
                user.setDirectInviteid(String.valueOf(userPre.getId())); // 邀请人的id , 需要查询
                user.setInviteRelation(String.valueOf(userPre.getId())); // 邀请人的id , 需要查询
            }

        }
        return user;
    }

//
//    @Override
//    public User getById(Serializable id) {
//        User user = super.getById(id);
//        if (user == null) {
//            throw new IllegalArgumentException("请输入正确的用户Id");
//        }
//        Byte seniorAuthStatus = null; // 用户的高级认证状态
//        String seniorAuthDesc = "";// 用户的高级认证未通过,原因
//        Integer reviewsStatus = user.getReviewsStatus(); // 用户被审核的状态 1通过,2拒绝,0,待审核"
//        if (reviewsStatus == null) { //为null 时,代表用户的资料没有上传
//            seniorAuthStatus = 3;
//            seniorAuthDesc = "资料未填写";
//        } else {
//            switch (reviewsStatus) {
//                case 1:
//                    seniorAuthStatus = 1;
//                    seniorAuthDesc = "审核通过";
//                    break;
//                case 2:
//                    seniorAuthStatus = 2;
//                    // 查询被拒绝的原因--->审核记录里面的
//                    // 时间排序,
//                    List<UserAuthAuditRecord> userAuthAuditRecordList = userAuthAuditRecordService.getUserAuthAuditRecordList(user.getId());
//                    if (!CollectionUtils.isEmpty(userAuthAuditRecordList)) {
//                        UserAuthAuditRecord userAuthAuditRecord = userAuthAuditRecordList.get(0);
//                        seniorAuthDesc = userAuthAuditRecord.getRemark();
//                    }
////                    seniorAuthDesc = "原因未知"; bug所在
//                    break;
//                case 0:
//                    seniorAuthStatus = 0;
//                    seniorAuthDesc = "等待审核";
//                    break;
//
//            }
//        }
//        user.setSeniorAuthStatus(seniorAuthStatus);
//        user.setSeniorAuthDesc(seniorAuthDesc);
//        return user;
//    }

    /**
     * 用户的实名认证
     *
     * @param id           用户的Id
     * @param userAuthForm 认证的表单数据
     * @return 认证的结果
     */
    @Override
    public boolean identifyVerify(Long id, UserAuthForm userAuthForm) {
        User user = getById(id);
        Assert.notNull(user, "认证的用户不存在");
        Byte authStatus = user.getAuthStatus();
        if (!authStatus.equals((byte) 0)) {
            throw new IllegalArgumentException("该用户已经认证成功了");
        }
        // 执行认证
        checkForm(userAuthForm); // 极验
        // 实名认证
        boolean check = IdAutoConfiguration.check(userAuthForm.getRealName(), userAuthForm.getIdCard());
        if (!check) {
            throw new IllegalArgumentException("该用户信息错误,请检查");
        }

        // 设置用户的认证属性
        user.setAuthtime(new Date());
        user.setAuthStatus((byte) 1);
        user.setRealName(userAuthForm.getRealName());
        user.setIdCard(userAuthForm.getIdCard());
        user.setIdCardType(userAuthForm.getIdCardType());

        return updateById(user);
    }

    private void checkForm(UserAuthForm userAuthForm) {
        userAuthForm.check(geetestLib, redisTemplate);
    }
}
