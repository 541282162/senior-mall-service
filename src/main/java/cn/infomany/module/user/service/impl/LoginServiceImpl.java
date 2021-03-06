package cn.infomany.module.user.service.impl;

import cn.infomany.common.constant.ErrorCodeEnum;
import cn.infomany.common.domain.Result;
import cn.infomany.common.exception.BusinessException;
import cn.infomany.common.service.BaseService;
import cn.infomany.module.user.dao.LoginDao;
import cn.infomany.module.user.dictionary.LoginUserStateEnum;
import cn.infomany.module.user.domain.dto.LoginDTO;
import cn.infomany.module.user.domain.entity.LoginEntity;
import cn.infomany.module.user.domain.vo.TokenInfo;
import cn.infomany.module.user.mapper.LoginMapper;
import cn.infomany.module.user.service.ILoginService;
import cn.infomany.util.LoginTokenUtil;
import cn.infomany.util.PasswordUtil;
import cn.infomany.util.TokenRedisUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * <p>
 * 登录表-login
 * </p>
 *
 * @author zjb
 * @since 2020-06-02 10:48:10
 */
@Service
@Slf4j
@DS("master")
public class LoginServiceImpl extends BaseService<LoginDao, LoginMapper, LoginEntity> implements ILoginService {


    @Autowired
    private TokenRedisUtil tokenRedisUtil;

    @Autowired
    private LoginTokenUtil loginTokenUtil;


    @Override
    public Result login(LoginDTO loginDTO) {

        // 通过用户名获取登录账号信息
        LoginEntity loginEntity = baseMapper.selectLoginByAccount(loginDTO.getAccount());
        if (Objects.isNull(loginEntity)) {
            log.error("登录账号:{}不存在，密码:{}", loginDTO.getAccount(), loginDTO.getPassword());
            throw new BusinessException(ErrorCodeEnum.INCORRECT_USERNAME_OR_PASSWORD);
        }

        // 比较密码是否一致
        String saltPassword = PasswordUtil.encryptedPassword(loginDTO.getPassword(), loginEntity.getSalt());
        if (!saltPassword.equals(loginEntity.getPassword())) {
            log.error("登录账号:{}，密码不匹配{}", loginDTO.getAccount(), loginDTO.getPassword());
            throw new BusinessException(ErrorCodeEnum.INCORRECT_USERNAME_OR_PASSWORD);
        }

        // 判断是否是正常的用户，减少判断次数
        Integer state = loginEntity.getState();
        if (!LoginUserStateEnum.NORMAL.is(state)) {
            if (LoginUserStateEnum.FREEZE.is(state)) {
                throw new BusinessException(ErrorCodeEnum.ACCOUNT_IS_FREEZE);
            }
        }

        // 生成token
        Long no = loginEntity.getNo();
        String signature = loginDTO.getSignature();
        TokenInfo tokenInfo = loginTokenUtil.generateToken(no, signature);

        // 将token放入redis
        tokenRedisUtil.save(no, signature, tokenInfo.getToken());


        log.info("账号:{},生成token信息:{}", loginDTO.getAccount(), tokenInfo);

        return Result.success(tokenInfo);
    }

    @Override
    public Result logout(Long no, String signature) {
        log.info("登出，用户编号:{},用户签名:{}", no, signature);
        // 删除redis中的token信息
        tokenRedisUtil.del(no, signature);
        return Result.success();
    }


    public Result addUser() {
        String salt = PasswordUtil.createSalt();
        String s = PasswordUtil.encryptedPassword("123456", salt);

        LoginEntity loginEntity = new LoginEntity();

        loginEntity.setPassword(s)
                .setSalt(salt)
                .setUsername("zjb")
                .setState(LoginUserStateEnum.NORMAL.getState())
                .setPhone("12345678912");

        baseMapper.insert(loginEntity);
        return null;
    }
}

