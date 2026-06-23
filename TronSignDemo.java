package com.mcpay.tron;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.mcpay.Main;
import com.mcpay.http.HttpTool;
import com.mcpay.util.Tooll;
import com.mcpay.websocket.LoginHander;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;

import com.alibaba.fastjson.JSON;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TronService {

    /**
     * ① maxSize：Map存储的最大值，类似队列，容量固定，当操作map容量超出限制时，最开始的元素就会依次过期，只保留最新的；
     * ② expiration：过期时间；
     * ③ expirationListener：过期监听,当条目过期时，将同步调用过期侦听器，并且在侦听器完成之前，
     *  将阻止对映射的写入操作。还可以在单独的线程池中配置和调用异步过期侦听器，而不会阻塞映射操作；
     * ④ expirationPolicy：过期策略，包括 ExpirationPolicy.ACCESSED 和 ExpirationPolicy.CREATED 两种；
     *      1）ExpirationPolicy.ACCESSED ：每进行一次访问，过期时间就会自动清零，重新计算；
     *      2）ExpirationPolicy.CREATED：在过期时间内重新 put 值的话，过期时间会清理，重新计算；
     * ⑤ variableExpiration：可变过期,条目可以具有单独可变的到期时间和策略：
     */
    public static  ExpiringMap<String, String> map_address = ExpiringMap.builder()
            .maxSize(1000)
            .expiration(8, TimeUnit.MINUTES)
            .variableExpiration()
            .expirationPolicy(ExpirationPolicy.CREATED)//过期策略,重新put则会重新计算
            .expirationListener((key, value) -> {
                Main.log("USDT已过期，key："+ key);
            })
            .build();

    public static String api_balance = "https://api.trongrid.io/v1/accounts/";
    //public static Map<String, String> map_address = new HashMap<>();
    public static Map<String, Integer> txid = new HashMap<>();
    public static int block_ID = 0;

    public TronService()
    {

    }

    /**
     * 查询tron账户余额
     * @param address
     */
    public static void getAddressBalance(String address)
    {
        try {
            String result = HttpTool.sendGet(api_balance + address);
            //System.out.println(result);
            BigDecimal trx = TronTooll.getBalance(Tooll.getSubString(result, "balance\":", ","));
            BigDecimal usdt = TronTooll.getHexMoney(Tooll.getSubString(result, AddressEnum.USDT.getAddress() + "\":\"", "\""));
            BigDecimal usdc = TronTooll.getBalance(Tooll.getSubString(result, AddressEnum.USDC.getAddress() + "\":\"", "\""));
            System.out.println(trx);
            System.out.println(usdt);
            System.out.println(usdc);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static enum AddressEnum {
        USDT("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"),
        USDC("TEkxiTehnzSmSe2XqrBj4w32RUN966rdz8");

        private final String address;
        AddressEnum(String address) {
            this.address = address;
        }
        public String getAddress() {
            return address;
        }
    }

    /**
     * 获取特定区块的所有Info 信息
     *
     * @return
     * @throws IOException
     */
    public static String getTransactionInfoByBlockNum(BigInteger num) throws IOException {
        String url = TronSignDemo.tronUrl + "/wallet/gettransactioninfobyblocknum";
        Map<String, Object> map = new HashMap<>();
        map.put("num", num);
        String param = JSON.toJSONString(map);
        return HttpTool.sendPost(url, param);
    }

    public static void updataNowBlock()
    {
        //String path = "C:\\Users\\Administrator\\Desktop\\333.txt"; // 替换为你的文件路径
        try {
            if(map_address.size() <= 0)
                return;
            //String content = new String(Files.readAllBytes(Paths.get(path)));
            String content = HttpTool.sendGet("https://api.trongrid.io/wallet/getnowblock");
            //JSONObject cc = new JSONObject(content);
            //System.out.println(cc.toString(4));
            String number = Tooll.getSubString(content, "number\":", ",");
            //System.out.println("-----块ID：" + number);
            int numberid = Integer.parseInt(number);
            if(!number.equals("") && numberid <= block_ID)
                return;//和上一次是同一区块不执行
            block_ID = numberid;
            //System.out.println("块高度：" + numberid);

            JSONObject json = new JSONObject(content);
            //System.out.println(json.toString(4));
            //交易列表
            JSONArray transactionsList = json.getJSONArray("transactions");
            for(int i = 0; i < transactionsList.length(); i++)
            {
                JSONObject transaction = transactionsList.getJSONObject(i);
                String ret = transaction.getJSONArray("ret").getJSONObject(0).getString("contractRet").toString();
                String txID = transaction.getString("txID");
                JSONArray contractList = transaction.getJSONObject("raw_data").getJSONArray("contract");
                for(int ii = 0; ii < contractList.length(); ii++)
                {
                    JSONObject contract = contractList.getJSONObject(ii);
                    String type = contract.getString("type");
                    switch(type)
                    {
                        case "TriggerSmartContract"://usdt

                            JSONObject value = contract.getJSONObject("parameter").getJSONObject("value");
                            if(!value.has("data"))
                            {
                                System.out.println("data信息不存在：" + value.toString());
                                return;
                            }
                            String data = value.getString("data");
                            if(data.substring(0, 8).equals("a9059cbb"))//判断是否是转账
                            {
                                String user = data.substring(30, 72);
                                if(user.startsWith("00"))
                                    user = "41" + user.substring(2);
                                String owner_address = value.getString("owner_address");

                                /*if(txid.containsKey(txID))
                                {
                                    txid.put(txID, txid.get(txID) + 1);
                                    Main.log("重复单号: " + txID + " 次数: " + txid.get(txID));
                                }else
                                {
                                    txid.put(txID, 1);
                                }*/
                                //System.out.println("USDT交易：" + txID);
                                //System.out.println("发送地址：" + TronTooll.HexAddressToUSDTaddress(owner_address));
                                //System.out.println("地址：" + data.substring(8, 72));

                                //System.out.println("hex地址：" + user);
                                //System.out.println("到账地址：" + TronTooll.HexAddressToUSDTaddress(user));
                                //System.out.println("转账金额：" + TronTooll.getHexMoney(data.substring(72)));
                                //System.out.println("状态：" + ret);
                                //检索监听地址
                                String address = TronTooll.HexAddressToUSDTaddress(user);
                                if(map_address.containsKey(address))
                                {
                                    //System.out.println("USDT交易：" + value.toString(4));
                                    Main.log("USDT交易：" + txID);
                                    Main.log("发送地址：" + TronTooll.HexAddressToUSDTaddress(owner_address));
                                    //System.out.println("地址：" + data.substring(8, 72));

                                    //System.out.println("hex地址：" + user);
                                    Main.log("到账地址：" + TronTooll.HexAddressToUSDTaddress(user));
                                    Main.log("转账金额：" + TronTooll.getHexMoney(data.substring(72)));
                                    Main.log("状态：" + ret);
                                    double num = TronTooll.getHexMoney(data.substring(72)).doubleValue();
                                    if(num >= 10)
                                    {
                                        LoginHander.checkPay(map_address.get(address), txID, num);
                                    }
                                }

                            }else
                            {
                                //智能合约
                            }
                            break;
                        case "TransferContract"://TRX
                            break;
                        case "DelegateResourceContract"://代理资源
                            break;
                        case "UnDelegateResourceContract"://回收资源
                            break;
                        case "FreezeBalanceV2Contract"://质押资产2.0
                            break;
                        case "VoteWitnessContract"://投票
                            break;
                        case "TransferAssetContract"://TRC-10转账
                            break;
                        case "WithdrawExpireUnfreezeContract"://提取未质押资产
                            break;
                        case "WithdrawBalanceContract"://提取收益
                            break;
                        default:
                            //System.out.println("交易哈希：" + txID);
                            //System.out.println("未知交易类型：" + type);
                            //System.out.println("---------------------------------");
                    }
                    //System.out.println(contract.getJSONObject("parameter"));
                }
                //s.optString("parameter");
                //

            }
        } catch (IOException e) {
            e.printStackTrace();
        }catch (JSONException e) {
            e.printStackTrace();
        }
    }

}
