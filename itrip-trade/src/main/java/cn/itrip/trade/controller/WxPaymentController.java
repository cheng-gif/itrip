package cn.itrip.trade.controller;

import cn.itrip.beans.dto.Dto;
import cn.itrip.beans.pojo.ItripHotelOrder;
import cn.itrip.common.DtoUtil;
import cn.itrip.trade.config.WXPayConfig;
import cn.itrip.trade.service.OrderService;
import cn.itrip.trade.wx.WXPayRequest;
import cn.itrip.trade.wx.WXPayUtil;
import io.swagger.annotations.ApiOperation;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by zezhong.shang on 17-8-11.
 */
@Controller
@RequestMapping("/api/wxpay/")
public class WxPaymentController {
    //**根据订单号生成二维码**//
    private Logger logger = Logger.getLogger(WxPaymentController.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private WXPayConfig wxPayConfig;

    @ApiOperation(value = "订单微信支付", httpMethod = "GET",
            protocols = "HTTP", produces = "application/xml", consumes = "application/x-www-form-urlencoded",
            response = String.class,
            notes = "客户端提交订单支付请求，对该API的返回结果不用处理，浏览器将自动跳转至微信支付二维码页面。<br><b>请使用普通表单提交，不能使用ajax异步提交。</b>")
    @RequestMapping(value = "/createqccode/{orderNo}", method = RequestMethod.GET)
    @ResponseBody
    public Dto createQcCode(@PathVariable String orderNo, HttpServletResponse response) {
        ItripHotelOrder order = null;
        HashMap<String, String> data = new HashMap<String, String>();
        System.out.println("值"+data);
        HashMap<String, Object> result = new HashMap<String, Object>();
        WXPayRequest wxPayRequest = new WXPayRequest(this.wxPayConfig);
        try {
            order = orderService.loadItripHotelOrder(orderNo);
            if (order == null || order.getOrderStatus() != 0) {
                return DtoUtil.returnFail("订单状态异常", "110001");
            }
            data.put("body", "爱旅行项目订单支付");
            data.put("out_trade_no", orderNo);
            data.put("device_info", "");
            data.put("total_fee", "1");
            data.put("spbill_create_ip", "47.92.146.135");
            data.put("notify_url", wxPayConfig.getNotifyUrl());
            Map<String, String> r = wxPayRequest.unifiedorder(data);
            System.out.println("值"+r);
            String resultCode = r.get("result_code");
            if (resultCode.equals("SUCCESS")) {
                result.put("hotelName", order.getHotelName());
                result.put("roomId", order.getRoomId());
                result.put("count", order.getCount());
                result.put("payAmount", order.getPayAmount());
                result.put("codeUrl", r.get("code_url"));
                return DtoUtil.returnDataSuccess(result);
            } else {
                logger.info(r.get("return_msg"));
                return DtoUtil.returnFail("订单支付异常", "110002");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return DtoUtil.returnFail("订单运行异常", "110003");
        }
    }
    /***
     * ww
     * 微信支付轮询订单，查看订单是否支付成功
     *
     * @param orderNo
     * @return
     */
    @ApiOperation(value = "订单微信支付刷单程序", httpMethod = "GET",
            protocols = "HTTP", produces = "application/xml", consumes = "application/x-www-form-urlencoded",
            response = String.class,
            notes = "前端用户检测订单是否成功的接口<br>如果订单状态为2则代表订单支付成功<b></b>")
    @RequestMapping(value = "/queryorderstatus/{orderNo}", method = RequestMethod.GET)
    @ResponseBody
    public Dto<ItripHotelOrder> queryOrderIsSuccess(@PathVariable String orderNo) {
        ItripHotelOrder order = null;
        try {
            order = orderService.loadItripHotelOrder(orderNo);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return DtoUtil.returnDataSuccess(order);
    }

    @RequestMapping(value = "/notify", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> paymentCallBack(HttpServletRequest request, HttpServletResponse response) {
        WXPayRequest wxPayRequest = new WXPayRequest(this.wxPayConfig);
        Map<String, String> result = new HashMap<String, String>();
        Map<String, String> params = null;
        try {
            InputStream inputStream;
            StringBuffer sb = new StringBuffer();
            inputStream = request.getInputStream();
            String s;
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            while ((s = in.readLine()) != null) {
                sb.append(s);
            }
            in.close();
            inputStream.close();
            params = WXPayUtil.xmlToMap(sb.toString());
            logger.info("1.notify-params>>>>>>>>>>>:" + params);
            boolean flag = wxPayRequest.isResponseSignatureValid(params);
            logger.info("2.notify-flag:" + flag);
            if (flag) {
                String returnCode = params.get("return_code");
                logger.info("3.returnCode:" + returnCode);
                if (returnCode.equals("SUCCESS")) {
                    String transactionId = params.get("transaction_id");
                    String outTradeNo = params.get("out_trade_no");
                    if (!orderService.processed(outTradeNo)) {
                        orderService.paySuccess(outTradeNo, 2, transactionId);
                    }
                    logger.info("4.订单：" + outTradeNo + " 交易完成" + ">>>" + transactionId);
                } else {
                    result.put("return_code", "FAIL");
                    result.put("return_msg", "支付失败");
                    logger.info("");
                }
            } else {
                result.put("return_code", "FAIL");
                result.put("return_msg", "签名失败");
                logger.info("签名验证失败>>>>>>>>>>>>");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
