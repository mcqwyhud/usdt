package com.mcpay.tron;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import com.mcpay.Main;
import com.mcpay.config.TronConfig;
import com.mcpay.http.HttpTool;
import com.mcpay.util.Tooll;
import com.mcpay.websocket.LoginHander;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.tron.common.crypto.ECKey;
import org.tron.common.crypto.ECKey.ECDSASignature;
import org.tron.common.parameter.CommonParameter;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Sha256Hash;
import org.tron.trident.abi.FunctionEncoder;
import org.tron.trident.abi.datatypes.Address;
import org.tron.trident.abi.datatypes.Type;
import org.tron.trident.abi.datatypes.generated.Uint256;
import org.tron.trident.proto.Contract.TransferContract;
import org.tron.trident.proto.Contract.TriggerSmartContract;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.Protocol.BlockHeader;
import org.tron.protos.Protocol.Transaction.Contract;

@SuppressWarnings("unused")
public class TronSignDemo {

    public static String tronUrl = "https://api.trongrid.io";
    private ECKey ecKey;
    private String fromAddress;
    private byte[] addressBytes;
    private String hexAddress;
    public String tronAddress;
    /**
     * 交易哈希
     */
    public String txid;

    public String USDTaddress = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";

    public TronSignDemo(String privateKey)
    {
        ecKey = ECKey.fromPrivate(ByteArray.fromHexString(privateKey));
        fromAddress = ByteArray.toHexString(ecKey.getPrivKeyBytes());
        addressBytes = ecKey.getAddress();
        hexAddress = ByteArray.toHexString(addressBytes);
        tronAddress = TronTooll.HexAddressToUSDTaddress(hexAddress);
    }

    public TronSignDemo(String privateKey,boolean isPass)
    {
        if (isPass){
            try {
                String key = Tooll.aesDecrypt(privateKey,Tooll.iv);
                ecKey = ECKey.fromPrivate(ByteArray.fromHexString(key));
                fromAddress = ByteArray.toHexString(ecKey.getPrivKeyBytes());
                addressBytes = ecKey.getAddress();
                hexAddress = ByteArray.toHexString(addressBytes);
                tronAddress = TronTooll.HexAddressToUSDTaddress(hexAddress);
            } catch (Exception e) {
                e.printStackTrace();
                Main.log("解密私匙错误: " + privateKey);
            }
        }
    }

    public boolean sendTrx(String toAddress, long amount)
    {
        if(!TronConfig.TrxTransfer){
            System.out.println("trx自动转账未开启");
            return false;
        }
        if(amount < 10000000)
        {
            System.out.println("警告: 发送TRX数量少于10个");
            return false;
        }
        Transaction transaction = createTransaction(toAddress,amount);
        byte[] transactionBytes = transaction.toByteArray();
        Transaction transactionSign = sign(transaction,ecKey);
        //System.out.println("transactionSign ::::: " + ByteArray.toHexString(transactionSign.toByteArray()));
        if (transactionSign == null){
            Main.log("trx签名失败,可能是密匙未解密成功");
            return false;
        }
        //广播交易
        boolean bool = postBroadcast(ByteArray.toHexString(transactionSign.toByteArray()));
        if(bool)
        {
            txid = TronTooll.getTransactionID(transaction);
        }
        return bool;
    }

    public boolean sendUsdt(String toAddress, long amount)
    {
        if(!TronConfig.TrxTransfer){
            System.out.println("Usdt自动转账未开启");
            return false;
        }
        if(amount < 3000000)
        {
            System.out.println("警告: 发送USDT数量少于3个");
            return false;
        }
        Transaction transaction = createTRC20Transaction(toAddress,amount);
        byte[] transactionBytes = transaction.toByteArray();
        //System.out.println(ByteArray.toHexString(transactionBytes));
        Transaction transactionSign = sign(transaction,ecKey);
        //System.out.println("transactionSign ::::: " + ByteArray.toHexString(transactionSign.toByteArray()));
        if (transactionSign == null){
            Main.log("usdt签名失败,可能是密匙未解密成功");
            return false;
        }
        //广播交易
        boolean bool = postBroadcast(ByteArray.toHexString(transactionSign.toByteArray()));
        if(bool)
        {
            txid = TronTooll.getTransactionID(transaction);
        }
        return bool;
    }

    /**
     * 返回状态
     */
    public static boolean transactionStatus(String userID,String hash,String address) {
        String res = getTransactionById(hash);
        if (res.isEmpty()) {
            return false;
        }
        String contractRet = Tooll.getSubString(res,"contractRet\":\"", "\"");
        if(!"SUCCESS".equals(contractRet))return false;
        String data = Tooll.getSubString(res,"data\":\"", "\"");
        System.out.println(data);
        if(!data.startsWith("a9059cbb"))return false;
        String user = data.substring(30, 72);
        if(user.startsWith("00"))
            user = "41" + user.substring(2);
        String add = TronTooll.HexAddressToUSDTaddress(user);
        String conAdd = Tooll.getSubString(res,"contract_address\":\"", "\"");
        if(!TronTooll.HexAddressToUSDTaddress(conAdd).equals("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t")
                || !add.equals(address))return false;
        double num = TronTooll.getHexMoney(data.substring(72)).doubleValue();
        //Main.log("合约地址" + TronTooll.HexAddressToUSDTaddress(conAdd));
        Main.log("账号" + userID);
        Main.log("地址" + address);
        Main.log("数量" + num);
        if(num > 1)
        {
            LoginHander.checkPay(userID,address,num);
            return true;
        }
        return false;
    }

    /**
     * 通过HASH获取Transaction信息
     *
     * @param hash
     * @return
     */
    public static String getTransactionById(String hash) {
        String url = tronUrl + "/walletsolidity/gettransactionbyid";
        Map<String, Object> map = new HashMap<>();
        map.put("value", hash);
        String param = JSON.toJSONString(map);
        try {
            String res = HttpTool.sendPost(url, param);
            System.out.println(res);
            return res;
        } catch (IOException e) {
            //e.printStackTrace();
        }
        return "";
    }

    /**
     * 查询USDT数量
     */
    @SuppressWarnings("rawtypes")
    public BigDecimal balanceOfUSDT(String address) {
        String url = tronUrl +"/wallet/triggerconstantcontract";
        JSONObject param = new JSONObject();
        param.put("owner_address", TronTooll.USDTaddressToHexAddress(address));
        param.put("contract_address", TronTooll.USDTaddressToHexAddress(this.USDTaddress));
        param.put("function_selector", "balanceOf(address)");
        List<Type> inputParameters = new ArrayList<>();
        inputParameters.add(new Address(TronTooll.USDTaddressToHexAddress(address)));
        param.put("parameter", FunctionEncoder.encodeConstructor(inputParameters));
        try {
            String result = HttpTool.sendPost(url, param.toJSONString());
            JSONObject obj = JSONObject.parseObject(result);
            JSONArray results = obj.getJSONArray("constant_result");
            if (results != null && results.size() > 0) {
                BigInteger amount = new BigInteger(results.getString(0), 16);
                return new BigDecimal(amount).divide(new BigDecimal(1000000), 6, RoundingMode.FLOOR);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return BigDecimal.ZERO;
    }

    /**
     * 查询TRX额度
     */
    public BigDecimal balanceOfTRX(String address) {
        String url = tronUrl + "/wallet/getaccount";
        ObjectNode param = Tooll.mapper.createObjectNode();
        param.put("address", TronTooll.USDTaddressToHexAddress(address));
        try {
            String result = HttpTool.sendPost(url, param.toString());
            BigInteger balance = BigInteger.ZERO;
            ObjectNode obj = Tooll.getStringToJson(result);
            //Main.log(obj.toString());
            BigInteger b = obj.get("balance").bigIntegerValue();
            if(b != null){
                balance = b;
            }
            return new BigDecimal(balance).divide(new BigDecimal(1000000),6, RoundingMode.FLOOR);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return BigDecimal.ZERO;
    }

    /**
     * 查询能量和带宽
     */
    public Map<String, Long> balanceOfEnergy(String address) {
        String url = tronUrl + "/wallet/getaccountresource";
        org.json.JSONObject param = new org.json.JSONObject();
        param.put("address", TronTooll.USDTaddressToHexAddress(address));
        Map<String,Long> map = new HashMap<>();
        try {
            String result = HttpTool.sendPost(url, param.toString());
            BigInteger balance = BigInteger.ZERO;
            org.json.JSONObject obj = new org.json.JSONObject(result);
            //Main.log(obj.toString(4));
            long freeNetLimit = obj.has("freeNetLimit") ? obj.getLong("freeNetLimit") : 0;
            long freeNetUsed = obj.has("freeNetUsed") ? obj.getLong("freeNetUsed") : 0;
            long net = freeNetLimit - freeNetUsed;
            long EnergyLimit = obj.has("EnergyLimit") ? obj.getLong("EnergyLimit") : 0;
            long EnergyUsed = obj.has("EnergyUsed") ? obj.getLong("EnergyUsed") : 0;
            long energy = EnergyLimit - EnergyUsed;
            Main.log("剩余带宽: " + net + "剩余能量: "+ energy);
            map.put("带宽",net);
            map.put("能量",energy);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }

    public Transaction createTRC20Transaction(String toAddress,long amount){
        Contract.Builder contractBuilder = Contract.newBuilder();
        Transaction.Builder transactionBuilder = Transaction.newBuilder();
        @SuppressWarnings("rawtypes")
        List<Type> inputParameters = new ArrayList<>();
        inputParameters.add(new Address(TronTooll.USDTaddressToHexAddress(toAddress)));
        inputParameters.add(new Uint256(new BigDecimal(amount).toBigInteger()));
        //a9059cbb转账标识符
        String parameter = "a9059cbb" + FunctionEncoder.encodeConstructor(inputParameters);
        TriggerSmartContract.Builder transferContractBuidler = TriggerSmartContract.newBuilder()
                .setOwnerAddress(ByteString.copyFrom(addressBytes))
                .setContractAddress(ByteString.copyFrom(TronTooll.USDTaddressToByte(USDTaddress)))
                .setData(ByteString.copyFrom(ByteArray.fromHexString(parameter)))
                ;
        Any any = Any.pack(transferContractBuidler.build());
        contractBuilder.setParameter(any);
        contractBuilder.setType(Contract.ContractType.TriggerSmartContract);

        transactionBuilder.getRawDataBuilder().addContract(contractBuilder)
                .setTimestamp(System.currentTimeMillis())
                .setExpiration(System.currentTimeMillis()+10*60*60*1000)
                .setFeeLimit(30000000);//最大能量限额

        BlockHeader.raw raw = getNowBlock();
        byte[] blockHash = Sha256Hash.of(true,raw.toByteArray()).getBytes();
        byte[] blockNum = ByteArray.fromLong(raw.getNumber());
        transactionBuilder.getRawDataBuilder()
                .setRefBlockHash(ByteString.copyFrom(ByteArray.subArray(blockHash,8,16)))
                .setRefBlockBytes(ByteString.copyFrom(ByteArray.subArray(blockNum,6,8)));
        return transactionBuilder.build();
    }

    public Transaction createTransaction(String toAddress,long amount){
        Transaction.Builder transactionBuilder = Transaction.newBuilder();
        Contract.Builder contractBuilder = Contract.newBuilder();
        TransferContract.Builder transferContractBuidler = TransferContract.newBuilder();
        transferContractBuidler.setOwnerAddress(ByteString.copyFrom(addressBytes));
        transferContractBuidler.setToAddress(ByteString.copyFrom(TronTooll.USDTaddressToByte(toAddress)));
        transferContractBuidler.setAmount(amount);
        //System.out.println("---::::"+ transferContractBuidler);
        Any any = Any.pack(transferContractBuidler.build());
        contractBuilder.setParameter(any);
        contractBuilder.setType(Contract.ContractType.TransferContract);
        transactionBuilder.getRawDataBuilder().addContract(contractBuilder)
                .setTimestamp(System.currentTimeMillis())
                .setExpiration(System.currentTimeMillis()+10*60*60*1000);
        BlockHeader.raw raw = getNowBlock();
        byte[] blockHash = Sha256Hash.of(true,raw.toByteArray()).getBytes();
        byte[] blockNum = ByteArray.fromLong(raw.getNumber());
        //System.out.println("block::::"+ByteArray.toHexString(blockHash));
        transactionBuilder.getRawDataBuilder()
                .setRefBlockHash(ByteString.copyFrom(ByteArray.subArray(blockHash,8,16)))
                .setRefBlockBytes(ByteString.copyFrom(ByteArray.subArray(blockNum,6,8)));
        //System.out.println("block::::" + transactionBuilder);
        return transactionBuilder.build();
    }

    public static BlockHeader.raw getNowBlock(){
        //使用RPC接口获取BlockHeader
//        Protocol.Block newestBlock = WalletApi.getBlock(-1);
//        return newestBlock.getBlockHeader().getRawData();

        //使用get请求获取blockHeader 目前只找到这个这个接口
        //https://apilist.tronscan.org/api/block/latest 这个接口获取的数据 少个version参数，打包广播的时候会出现 TAPOS_ERROR
        String nowBlockStr = getHttp("https://api.trongrid.io/wallet/getnowblock");
        JSONObject jsonObject = JSON.parseObject(nowBlockStr);
        String block_header = jsonObject.getString("block_header");
        JSONObject raw_data = JSON.parseObject(block_header);
        JSONObject data = JSON.parseObject(raw_data.getString("raw_data"));

        BlockHeader.raw.Builder rawBuidler = BlockHeader.raw.newBuilder();

        rawBuidler.setNumber(data.getLong("number"));
        rawBuidler.setTxTrieRoot(ByteString.copyFrom(ByteArray.fromHexString(data.getString("txTrieRoot"))));
        rawBuidler.setWitnessAddress(ByteString.copyFrom(ByteArray.fromHexString(data.getString("witness_address"))));
        rawBuidler.setParentHash(ByteString.copyFrom(ByteArray.fromHexString(data.getString("parentHash"))));
        rawBuidler.setTimestamp(data.getLong("timestamp"));
        rawBuidler.setVersion(data.getIntValue("version"));
        return rawBuidler.build();
    }

    public static String getHttp(String url) {
        try {
            HttpGet get = new HttpGet((url));
            HttpClient client = HttpClients.createDefault();
            HttpResponse response = client.execute(get);
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static boolean postBroadcast(String sgin){
        try {
            //广播地址：https://apilist.tronscan.org/api/broadcast
            HttpPost post = new HttpPost("https://apilist.tronscan.org/api/broadcast");
            post.setEntity(new StringEntity("{\"transaction\":\""+sgin+"\"}"));
            HttpClient client = HttpClients.createDefault();
            HttpResponse response = client.execute(post);
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            System.out.println("广播结果: " + result);
            org.json.JSONObject json = new org.json.JSONObject(result);
            if(!json.has("success")) return false;
            if(json.getBoolean("success"))
            {
                System.out.println("transaction: " + json.getString("transaction"));
            }
            return json.getBoolean("success");
        }catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }

    public static Transaction sign(Transaction transaction, ECKey myKey) {
        if (myKey == null){
            return null;
        }
        Transaction.Builder transactionBuilderSigned = transaction.toBuilder();

        byte[] hash = Sha256Hash.hash(CommonParameter
                .getInstance().isECKeyCryptoEngine(), transaction.getRawData().toByteArray());
        List<Contract> listContract = transaction.getRawData().getContractList();
        for (int i = 0; i < listContract.size(); i++) {
            ECDSASignature signature = myKey.sign(hash);
            ByteString bsSign = ByteString.copyFrom(signature.toByteArray());
            transactionBuilderSigned.addSignature(
                    bsSign);//Each contract may be signed with a different private key in the future.
        }

        transaction = transactionBuilderSigned.build();
        return transaction;
    }

    /*public static void main(String[] args) {

    	String privateKey = "";
        byte[] privateByte = ByteArray.fromHexString(privateKey);
        ECKey ecKey = ECKey.fromPrivate(privateByte);
        byte[] from = ecKey.getAddress();
        byte[] to = Base58Check.base58ToBytes("TPs3X9wZMU63oMmNGrSuXQN9E2aF4qtRch");
        System.out.println("from::"+ByteArray.toHexString(from));
        System.out.println("to::"+ByteArray.toHexString(to));

        long amount = 321;
        Transaction transaction = createTransaction(from,to,amount);
        byte[] transactionBytes = transaction.toByteArray();
        System.out.println(ByteArray.toHexString(transactionBytes));
        Transaction transactionSign = sign(transaction,ecKey);
        System.out.println("transactionSign ::::: " + ByteArray.toHexString(transactionSign.toByteArray()));

        //g广播交易
        postBroadcast(ByteArray.toHexString(transactionSign.toByteArray()));
    }*/
}
