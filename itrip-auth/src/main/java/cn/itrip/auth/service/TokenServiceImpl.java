package cn.itrip.auth.service;

import cn.itrip.auth.exception.TokenValidationFailedException;
import cn.itrip.beans.pojo.ItripUser;
import cn.itrip.common.MD5;
import cn.itrip.common.RedisAPI;
import cn.itrip.common.UserAgentUtil;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

@Service("tokenService")
public class TokenServiceImpl implements TokenService{
    @Resource
    private RedisAPI redisAPI;

    private int expire = SESSION_TIMEOUT;// 2h
    public int getExpire() {
        return expire;
    }

    public void setExpire(int expire) {
        this.expire = expire;
    }
    /**
     * 生成token
     * 格式:"token":"token:PC-usercode-userID-creationDate-random"
     * @param itripUser
     * @return
     */
    @Override
    public String generateToken(String agent, ItripUser itripUser) {
        StringBuilder str = new StringBuilder();
        str.append("token:");
        if (UserAgentUtil.CheckAgent(agent)){
            str.append("MOBILE-");
        }else {
            str.append("PC-");
        }
        str.append(MD5.getMd5(itripUser.getUserCode(),32) +"-");
        str.append(itripUser.getId().toString()+"-");
        str.append(new SimpleDateFormat("yyyyMMddHHmmsss").format(new Date())+"-");
        str.append(MD5.getMd5(agent,6));
        return str.toString();
    }

    /**
     * 保存token
     * @param token
     * @param itripUser
     */
    @Override
    public void save(String token, ItripUser itripUser) {
        if(token.startsWith("token:PC-")){  //如果是PC端，则设置有效期
            redisAPI.set(token,2*60*60, JSON.toJSONString(itripUser));
        }else{  //如果是移动端，则永久保存
            redisAPI.set(token,JSON.toJSONString(itripUser));
        }
    }

    @Override
    public ItripUser load(String token) {
        return JSON.parseObject(redisAPI.get(token), ItripUser.class);
    }

    @Override
    public void delete(String token) {
        if (redisAPI.exist(token))
            redisAPI.delete(token);
    }

    private boolean exists(String token) {
        return redisAPI.exist(token);
    }

    @Override
    public String replaceToken(String agent, String token)
            throws TokenValidationFailedException {

        // 验证旧token是否有效
        if (!exists(token)) {// token不存在
            throw new TokenValidationFailedException("未知的token或 token已过期");// 终止置换
        }
        Date TokenGenTime;// token生成时间
        try {
            String[] tokenDetails = token.split("-");
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            TokenGenTime = formatter.parse(tokenDetails[3]);
        } catch (ParseException e) {
            throw new TokenValidationFailedException("token格式错误:" + token);
        }

        long passed = Calendar.getInstance().getTimeInMillis()
                - TokenGenTime.getTime();// token已产生时间
        if (passed < REPLACEMENT_PROTECTION_TIMEOUT * 1000) {// 置换保护期内
            throw new TokenValidationFailedException("token处于置换保护期内，剩余"
                    + (REPLACEMENT_PROTECTION_TIMEOUT * 1000 - passed) / 1000
                    + "(s),禁止置换");
        }
        // 置换token
        String newToken = "";
        ItripUser user = this.load(token);
        long ttl = redisAPI.ttl(token);// token有效期（剩余秒数 ）
        if (ttl > 0 || ttl == -1) {// 兼容手机与PC端的token在有效期
            newToken = this.generateToken(agent, user);
            this.save(newToken, user);// 缓存新token
            redisAPI.set(token, this.REPLACEMENT_DELAY,
                    JSON.toJSONString(user));// 2分钟后旧token过期，注意手机端由永久有效变为2分钟（REPLACEMENT_DELAY默认值）后失效
        } else {// 其它未考虑情况，不予置换
            throw new TokenValidationFailedException("当前token的过期时间异常,禁止置换");
        }
        return newToken;
    }

    @Override
    public boolean validate(String agent, String token) {
        if (!exists(token)) {// token不存在
            return false;
        }
        try {
            Date TokenGenTime;// token生成时间
            String agentMD5;
            String[] tokenDetails = token.split("-");
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            TokenGenTime = formatter.parse(tokenDetails[3]);
            long passed = Calendar.getInstance().getTimeInMillis()
                    - TokenGenTime.getTime();
            if(passed>this.SESSION_TIMEOUT*1000)
                return false;
            agentMD5 = tokenDetails[4];
            if(MD5.getMd5(agent, 6).equals(agentMD5))
                return true;
        } catch (ParseException e) {
            return false;
        }
        return false;
    }
}
