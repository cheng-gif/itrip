package cn.itrip.auth.controller;

import cn.itrip.auth.service.UserService;
import cn.itrip.beans.dto.Dto;
import cn.itrip.beans.pojo.ItripUser;
import cn.itrip.beans.vo.userinfo.ItripUserVO;
import cn.itrip.common.DtoUtil;
import cn.itrip.common.ErrorCode;
import cn.itrip.common.MD5;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.regex.Pattern;

@Controller
@RequestMapping(value = "/api")
public class UserController {
    @Resource
    private UserService userService;

    /**
     * 使用手机注册
     * @return
     */
    @ApiOperation(value = "使用手机注册",httpMethod = "post",
            protocols = "http",produces = "application/json",response = Dto.class,notes = "使用手机注册")
    @RequestMapping(value="/registerbyphone",method=RequestMethod.POST,produces = "application/json")
    @ResponseBody
    public Dto registerbyphone(@RequestBody ItripUserVO itripUserVO){
        try {
            if(!validPhone(itripUserVO.getUserCode()))
                return  DtoUtil.returnFail("请使用正确的手机号注册", ErrorCode.AUTH_ILLEGAL_USERCODE);

            ItripUser user=new ItripUser();
            user.setUserCode(itripUserVO.getUserCode());
            user.setUserPassword(itripUserVO.getUserPassword());
            user.setUserType(0);
            user.setUserName(itripUserVO.getUserName());
            if (null == userService.findByUsername(user.getUserCode())) {
                user.setUserPassword(MD5.getMd5(user.getUserPassword(), 32));
                userService.itriptxCreateUserByPhone(user);
                return DtoUtil.returnSuccess();
            }else
            {
                return DtoUtil.returnFail("用户已存在，注册失败", ErrorCode.AUTH_USER_ALREADY_EXISTS);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return DtoUtil.returnFail(e.getMessage(), ErrorCode.AUTH_UNKNOWN);
        }
    }

    @ApiOperation(value = "使用手机验证",httpMethod = "post",
            protocols = "http",produces = "application/json",response = Dto.class,notes = "使用手机验证")
    @RequestMapping(value = "/validatephone",method = RequestMethod.PUT,produces = "application/json")
    @ResponseBody
    public  Dto validatePhone(String user, String code){
        try {
            if(userService.validatePhone(user, code))
            {
                return DtoUtil.returnSuccess("验证成功");
            }else{
                return DtoUtil.returnSuccess("验证失败");
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return DtoUtil.returnFail("验证失败", ErrorCode.AUTH_ACTIVATE_FAILED);
        }
    }
    /**
     * 验证是否合法的手机号
     * @param phone
     * @return
     */
    private boolean validPhone(String phone) {
        String regex="^1[3578]{1}\\d{9}$";
        return Pattern.compile(regex).matcher(phone).find();
    }
    /**			 *
     * 合法E-mail地址：
     * 1. 必须包含一个并且只有一个符号“@”
     * 2. 第一个字符不得是“@”或者“.”
     * 3. 不允许出现“@.”或者.@
     * 4. 结尾不得是字符“@”或者“.”
     * 5. 允许“@”前的字符中出现“＋”
     * 6. 不允许“＋”在最前面，或者“＋@”
     */
    private boolean validEmail(String email){

        String regex="^\\s*\\w+(?:\\.{0,1}[\\w-]+)*@[a-zA-Z0-9]+(?:[-.][a-zA-Z0-9]+)*\\.[a-zA-Z]+\\s*$"  ;
        return Pattern.compile(regex).matcher(email).find();
    }
    /**
     * 使用邮箱注册
     * @param userVO
     * @return
     */
    @RequestMapping(value="/doregister",method=RequestMethod.POST,produces = "application/json")
    @ResponseBody
    public Dto doRegister(@RequestBody ItripUserVO userVO) {
        if(!validEmail(userVO.getUserCode()))
            return  DtoUtil.returnFail("请使用正确的邮箱地址注册",ErrorCode.AUTH_ILLEGAL_USERCODE);

        try {
            ItripUser user=new ItripUser();
            user.setUserCode(userVO.getUserCode());
            user.setUserPassword(userVO.getUserPassword());
            user.setUserType(0);
            user.setUserName(userVO.getUserName());
//			user.setFlatID(userVO.getFlatID());
//			user.setWeChat(userVO.getWeChat());
//			user.setQQ(userVO.getQQ());
//			user.setWeibo(userVO.getWeibo());
//			user.setBaidu(userVO.getBaidu());
            if (null == userService.findByUsername(user.getUserCode())) {
                user.setUserPassword(MD5.getMd5(user.getUserPassword(), 32));
                userService.itriptxCreateUser(user);
                return DtoUtil.returnSuccess();
            }else
            {
                return DtoUtil.returnFail("用户已存在，注册失败", ErrorCode.AUTH_USER_ALREADY_EXISTS);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return DtoUtil.returnFail(e.getMessage(), ErrorCode.AUTH_UNKNOWN);
        }

    }

    @RequestMapping(value="/activate",method=RequestMethod.PUT,produces= "application/json")
    @ResponseBody
    public  Dto activate(@RequestParam String user, @RequestParam String code){
        try {
            if(userService.activate(user, code))
            {
                return DtoUtil.returnSuccess("激活成功");
            }else{
                return DtoUtil.returnSuccess("激活失败");
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return DtoUtil.returnFail("激活失败", ErrorCode.AUTH_ACTIVATE_FAILED);
        }

    }
    /**
     * 检查用户是否已注册
     * @param name
     * @return
     */
    @RequestMapping(value="/ckusr",method=RequestMethod.GET,produces= "application/json")
    @ResponseBody
    public Dto checkUser(String name) {
        try {
		/*	if(!validEmail(name))
				return  DtoUtil.returnFail("请使用正确的邮箱地址注册",ErrorCode.AUTH_ILLEGAL_USERCODE);*/
            if (null == userService.findByUsername(name))
            {
                return DtoUtil.returnSuccess("用户名可用");
            }
            else
            {
                return DtoUtil.returnFail("用户已存在，注册失败", ErrorCode.AUTH_USER_ALREADY_EXISTS);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return DtoUtil.returnFail(e.getMessage(), ErrorCode.AUTH_UNKNOWN);
        }
    }
}
