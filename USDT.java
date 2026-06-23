package com.mcpay.tron;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import com.mcpay.Main;
import com.mcpay.http.HttpTool;
import com.mcpay.util.Tooll;
import org.bouncycastle.util.encoders.Hex;
import org.tron.common.crypto.ECKey;
import org.tron.common.parameter.CommonParameter;
import org.tron.common.utils.Sha256Hash;
import org.tron.protos.Protocol;
import org.tron.trident.utils.Base58Check;
import org.tron.trident.core.utils.ByteArray;

public class TronTooll {

    /**
     * 获取交易哈希 txid
     * @param transaction
     * @return
     */
    public static String getTransactionID(Protocol.Transaction transaction)
    {
        return Sha256Hash.of(CommonParameter.getInstance().isECKeyCryptoEngine(),
                transaction.getRawData().toByteArray()).toString();
        //return txid;
    }

    /**
     * 检查usdt地址是否正确
     * @param address
     * @return
     */
    public static boolean checkUsdtAddress(String address){
        return address.startsWith("T") && address.length() == 34;
    }

    /**
     * 获取汇率
     */
    public static void getUsdtToCNY()
    {
        try {
            String res = HttpTool.sendGet("https://www.chinamoney.com.cn/r/cms/www/chinamoney/data/fx/rfx-Dollar-close-rate.json");
            String s = Tooll.getSubString(res, "closeSpotPrice\":\"", "\"");
            if(!s.equals(""))
            {
                USDT.usdtToCNY = (int)(Double.parseDouble(s) * 100) / 100.0;
                Main.log("当前汇率: " + USDT.usdtToCNY);
            }
        } catch (IOException e) {
            Main.log("查询汇率出错");
        }
    }

    /**
     * USDT地址转Hex地址.如果返回为空,则不是正确的usdt地址
     * @param usdtAddress USDT地址 例:TVtwXQg4dRii7BBXAojGMb1sqiiyt4zg9p
     * @return 返回 Hex地址 例:41da93eeb8f001b49b9603a67c83dc738d529ac4d1
     */
    public static String USDTaddressToHexAddress(String usdtAddress)
    {
        try{
            return Hex.toHexString(Base58Check.base58ToBytes(usdtAddress));
        }catch(Exception e)
        {
            System.out.println(e);
        }
        return "";
    }

    public static byte[] USDTaddressToByte(String usdtAddress)
    {
        return Base58Check.base58ToBytes(usdtAddress);
    }

    /**
     * privateKey转Byte
     * @param privateKey 私匙
     * @return byte
     */
    public static byte[] privateKeyToByte(String privateKey)
    {
        return Hex.decode(privateKey);
    }

    /**
     * Hex地址转USDT地址.如果返回为空,则不是正确的Hex地址
     * @param HexAddress  Hex地址 例:41da93eeb8f001b49b9603a67c83dc738d529ac4d1
     * @return 返回USDT地址 例:TVtwXQg4dRii7BBXAojGMb1sqiiyt4zg9p
     */
    public static String HexAddressToUSDTaddress(String HexAddress)
    {
        try{
            return Base58Check.bytesToBase58(Hex.decode(HexAddress));
        }catch(Exception e)
        {
            System.out.println(e);
        }
        return "";
    }

    /**
     *
     * @param hexString 十六进制
     * @return
     */
    public static BigDecimal getHexMoney(String hexString)
    {
        try{
            return new BigDecimal(new BigInteger(hexString, 16)).divide(new BigDecimal(1000000), 6, RoundingMode.FLOOR);
        }catch (Exception e) {
            e.printStackTrace();
            return new BigDecimal(0);
        }
    }

    /**
     *
     * @param balance 十进制
     * @return
     */
    public static BigDecimal getBalance(String balance)
    {
        return new BigDecimal(balance == "" ? "0" : balance).divide(new BigDecimal(1000000), 6, RoundingMode.FLOOR);
    }

    /**
     * 激活地址
     *
     * @param address
     * @return
     */
    public static String createAccount(String address) {
        /*String url = TronSignDemo.tronUrl + "/wallet/createaccount";
        Map<String, Object> map = new HashMap<>();
        map.put("owner_address", ByteArray.toHexString(WalletApi.decodeFromBase58Check(trxAddress)));
        map.put("account_address", ByteArray.toHexString(WalletApi.decodeFromBase58Check(address)));
        String param = JSON.toJSONString(map);
        return signAndBroadcast(postForEntity(url, param).getBody(), privateKey);*/
        return address;
    }

    public static SecureRandom random = new SecureRandom();

    public static Map<String, String> createAddress() {
        ECKey eCkey = new ECKey(random);
        String privateKey = ByteArray.toHexString(eCkey.getPrivKeyBytes());
        byte[] addressBytes = eCkey.getAddress();
        String hexAddress = ByteArray.toHexString(addressBytes);
        Map<String, String> addressInfo = new HashMap<>(3);
        addressInfo.put("privateKey", privateKey);
        String address = TronTooll.HexAddressToUSDTaddress(hexAddress);
		System.out.println("公匙：" + ByteArray.toHexString(eCkey.getPubKey()));
        System.out.println("地址：" + TronTooll.HexAddressToUSDTaddress(hexAddress));
        System.out.println("私匙：" + privateKey);
        System.out.println("十六进制地址：" + hexAddress);
        if(address.length() > 0)
        {
            addressInfo.put("address", address);
            return addressInfo;
        }
        return null;
    }

}
