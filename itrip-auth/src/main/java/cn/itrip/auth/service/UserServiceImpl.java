package cn.itrip.auth.service;

import cn.itrip.beans.pojo.ItripUser;
import cn.itrip.common.EmptyUtils;
import cn.itrip.common.MD5;
import cn.itrip.common.RedisAPI;
import cn.itrip.dao.user.ItripUserMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("userService")
public class UserServiceImpl implements UserService{
    @Resource
    private ItripUserMapper itripUserMapper;
    @Resource
    private SmsService smsService;
    @Resource
    private RedisAPI redisAPI;
    @Resource
    private MailService mailService;
    private int expire = 2; //过期时间一分钟

    @Override
    /**
     * 使用手机号创建用户账号
     * @param user
     * @throws Exception
     */
    public void itriptxCreateUserByPhone(ItripUser user) throws Exception {
        //发送短信验证码
        String code=String.valueOf(MD5.getRandomCode());
        smsService.send("15273072052", "1", new String[]{code,"5"});
        //缓存验证码
        String key="activation:"+user.getUserCode();
        redisAPI.set(key, expire*60, code);
        //保存用户信息
        itripUserMapper.insertItripUser(user);
    }

    @Override
    /**
     * 短信验证手机号
     * @param phoneNumber
     * @return
     * @throws Exception
     */
    public boolean validatePhone(String phoneNumber, String code) throws Exception {
        String key = "activation:"+phoneNumber;
        if (redisAPI.exist(key)) {
            if (redisAPI.get(key).equals(code)) {
                ItripUser itripUser = findByUsername(phoneNumber);
                if (EmptyUtils.isNotEmpty(itripUser)) {
                    itripUser.setActivated(1);//激活用户
                    itripUser.setUserType(0);//自注册用户
                    itripUser.setFlatID(itripUser.getId());
                    itripUserMapper.updateItripUser(itripUser);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     *
     * @param username
     * @return
     * @throws Exception
     */
    @Override
    public ItripUser findByUsername(String username) throws Exception {
        Map<String, Object> param=new HashMap();
        param.put("userCode", username);
        List<ItripUser> list= itripUserMapper.getItripUserListByMap(param);
        if(list.size()>0)
            return list.get(0);
        else
            return null;
    }

    /**
     *
     * @param user
     * @throws Exception
     */
    @Override
    public void updateUser(ItripUser user) throws Exception {
        itripUserMapper.updateItripUser(user);
    }

    /**
     *
     * @param email 用户注册油箱
     * @param code 激活码
     * @return
     * @throws Exception
     */
    @Override
    public boolean activate(String email, String code) throws Exception {
        String key="activation:"+email;
        if(redisAPI.exist(key))
            if(redisAPI.get(key).equals(code)){
                ItripUser user=this.findByUsername(email);
                if(EmptyUtils.isNotEmpty(user))
                {
                    user.setActivated(1);//激活用户
                    user.setUserType(0);//自注册用户
                    user.setFlatID(user.getId());
                    itripUserMapper.updateItripUser(user);
                    return true;
                }
            }
        return false;
    }

    /**
     * 创建用户
     * @param user
     * @throws Exception
     */
    public void itriptxCreateUser(ItripUser user) throws Exception {
        //发送激活邮件
        String activationCode = MD5.getMd5(new Date().toLocaleString(), 32);
        mailService.sendActivationMail(user.getUserCode(), activationCode);
        //保存用户信息
        itripUserMapper.insertItripUser(user);
    }

    @Override
    public ItripUser login(String userCode, String userPassword) throws Exception{
        ItripUser itripUser = this.findByUsername(userCode);
        if (itripUser!=null && userPassword.equals(itripUser.getUserPassword())){
            if(itripUser.getActivated()!=1){    //如果用户未被激活
                throw new Exception("用户未被激活！");
            }
            return itripUser;
        }else{
            return null;
        }
    }
}
