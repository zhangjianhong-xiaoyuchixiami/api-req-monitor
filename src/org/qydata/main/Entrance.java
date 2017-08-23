package org.qydata.main;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.qydata.po.ApiBan;
import org.qydata.tools.CalendarAssistTool;
import org.qydata.tools.SendEmail;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by jonhn on 2017/4/19.
 */
public class Entrance {

    private static String [] to  = {"ld@qianyandata.com","it@qianyandata.com","zhangjianhong@qianyandata.com"};
    //private static String [] to  = {"zhangjianhong@qianyandata.com"};

    public static SqlSession masterSqlSession(){
        //String resource_master = "mybatis_master_test.xml";
        String resource_master = "mybatis_master.xml";
        InputStream is = Entrance.class.getClassLoader().getResourceAsStream(resource_master);
        SqlSessionFactory sessionFactory = new SqlSessionFactoryBuilder().build(is);
        SqlSession session = sessionFactory.openSession();
        return session;
    }

    public static SqlSession slaveSqlSession(){
        //String resource_slave = "mybatis_slave_test.xml";
        String resource_slave = "mybatis_slave.xml";
        InputStream is = Entrance.class.getClassLoader().getResourceAsStream(resource_slave);
        SqlSessionFactory sessionFactory = new SqlSessionFactoryBuilder().build(is);
        SqlSession session = sessionFactory.openSession();
        return session;
    }

    public static void main(String[] args) {

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:SS");

        List<ApiBan> apiList = queryAllApiBan();
        if (apiList != null && apiList.size() > 0){
            for (int i = 0; i < apiList.size() ; i++) {
                ApiBan apiBanJudge = apiList.get(i);
                if (apiBanJudge != null){
                    int apiId = apiBanJudge.getApiId();
                    int fc = apiBanJudge.getFc();
                    String ts = sdf.format(apiBanJudge.getTs());
                    String product = "";
                    if (apiBanJudge.getPartnerName() != null){
                        product = apiBanJudge.getApiTypeName() + "@" + apiBanJudge.getVendorName() + "-" + apiBanJudge.getPartnerName() ;
                    }else {
                        product = apiBanJudge.getApiTypeName() + "@" + apiBanJudge.getVendorName();
                    }
                    Integer recoverFlag = queryApiMonitorRecover(apiId);
                    if (recoverFlag == null){
                        insertApiMonitorRecover(apiId);
                    }else {
                        updateApiMonitorRecover(apiId);
                    }
                    List<ApiBan> apiBanList = queryApiBan(apiId);
                    if (apiBanList != null && apiBanList.size() > 0){
                        int totleCount = 0;
                        int failCount = 0;
                        long failRate = 0;
                        String failContent = "无";
                        int resTime = 0;
                        String companyName = "无";
                        for (int j = 0; j < apiBanList.size() ; j++) {
                            ApiBan apiBan = apiBanList.get(j);
                            if (apiBan.getTotleId() != null){
                                totleCount ++ ;
                            }
                            if (apiBan.getFailId() != null){
                                failCount ++ ;
                            }
                        }
                        ApiBan apiBan = apiBanList.get(apiBanList.size()-1);
                        if (apiBan != null){
                            if (apiBan.getContent() != null){
                                failContent = apiBan.getContent();
                            }
                            if (apiBan.getResTime() != null){
                                resTime = apiBan.getResTime();
                            }
                            if (apiBan.getCompanyName() != null){
                                companyName = apiBan.getCompanyName();
                            }
                            if (totleCount != 0){
                                failRate = Math.round(((double)failCount/(double)totleCount)*100);
                            }
                            Integer lastFc = queryApiMonitor(apiId);
                            boolean flag = false;
                            if (lastFc != null){
                                if (lastFc != fc){
                                    flag = true;
                                }
                            }else {
                                flag = true;
                            }
                            if (flag){
                                try {
                                    String title = product+"请求失败提示";
                                    String content = "<html>" +
                                            "<body>" +
                                            "<div><span>产品：</span><span>"+ product +"</span></div>" +
                                            "<div><span>最近请求连续失败次数：</span><span>"+ fc +"</span><span>次</span></div>" +
                                            "<div><span>最后请求失败时间：</span><span>"+ ts +"</span></div>" +
                                            "<div><span>近一小时内请求次数：</span><span>"+ totleCount +"</span><span>次</span></div>" +
                                            "<div><span>近一小时内失败次数：</span><span>"+ failCount +"</span><span>次</span></div>" +
                                            "<div><span>失败率：</span><span>"+ failRate +"</span><span>%</span></div>" +
                                            "<div><span>最后一条失败记录的响应内容：</span><span>"+ failContent +"</span></div>" +
                                            "<div><span>最后一条失败记录的响应时间：</span><span>"+ resTime +"</span><span>豪秒</span></div>" +
                                            "<div><span>最后一条失败记录所影响的客户：</span><span>"+ companyName +"</span></div>" +
                                            "</body>" +
                                            "</html>";
                                    SendEmail.sendMail(to,title,content);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            if (lastFc == null){
                                insertApiMonitor(apiId,fc);
                            }else {
                                updateApiMonitor(apiId,fc);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 查询连续失败5次以上的产品
     * @return
     */
    public static List<ApiBan> queryAllApiBan(){
        SqlSession session = slaveSqlSession();
        String queryAllApiBan = "org.qydata.mapper.ApiBanMapper.queryAllApiBan";
        List<ApiBan> apiList = session.selectList(queryAllApiBan);
        session.close();
        return apiList;
    }

    /**
     * 查询指定产品恢复标志
     * @param apiId
     * @return
     */
    public static Integer queryApiMonitorRecover(Integer apiId){
        SqlSession session = slaveSqlSession();
        String queryApiMonitorRecover = "org.qydata.mapper.ApiBanMapper.queryApiMonitorRecover";
        Map<String, Object> queryApiMonitorRecoverParam = new HashMap<>();
        queryApiMonitorRecoverParam.put("apiId", apiId);
        Integer recoverFlag = session.selectOne(queryApiMonitorRecover, queryApiMonitorRecoverParam);
        session.close();
        return recoverFlag;
    }

    /**
     * 插入指定产品恢复标志
     * @param apiId
     */
    public static void  insertApiMonitorRecover(Integer apiId){
        SqlSession session = masterSqlSession();
        String insertApiMonitorRecover = "org.qydata.mapper.ApiBanMapper.insertApiMonitorRecover";
        Map<String, Object> param = new HashMap<>();
        param.put("apiId", apiId);
        param.put("recoverFlag", 0);
        param.put("sendFlag", 0);
        session.insert(insertApiMonitorRecover,param);
        session.commit();
        session.close();
    }

    /**
     * 修改指定产品恢复标志
     * @param apiId
     */
    public static void updateApiMonitorRecover(Integer apiId){
        SqlSession session = masterSqlSession();
        String updateApiMonitorRecover = "org.qydata.mapper.ApiBanMapper.updateApiMonitorRecover";
        Map<String, Object> param = new HashMap<>();
        param.put("apiId", apiId);
        param.put("recoverFlag", 0);
        param.put("sendFlag", 0);
        session.update(updateApiMonitorRecover,param);
        session.commit();
        session.close();
    }

    /**
     * 查询指定产品近一小时请求日志
     * @param apiId
     * @return
     */
    public static List<ApiBan> queryApiBan(Integer apiId){
        SqlSession session = slaveSqlSession();
        String queryApiBan = "org.qydata.mapper.ApiBanMapper.queryApiBan";
        Map<String, Object> queryApiBanParam = new HashMap<>();
        queryApiBanParam.put("time", CalendarAssistTool.getCurrentLastHour());
        queryApiBanParam.put("apiId", apiId);
        List<ApiBan> apiBanList = session.selectList(queryApiBan, queryApiBanParam);
        session.close();
        return apiBanList;
    }

    /**
     * 查询指定产品上一次连续失败次数
     * @param apiId
     * @return
     */
    public static Integer queryApiMonitor(Integer apiId){
        SqlSession session = slaveSqlSession();
        String queryApiMonitor = "org.qydata.mapper.ApiBanMapper.queryApiMonitor";
        Map<String, Object> mapApiMonitor = new HashMap<>();
        mapApiMonitor.put("apiId", apiId);
        Integer lastFc = session.selectOne(queryApiMonitor, mapApiMonitor);
        session.close();
        return lastFc;
    }

    /**
     * 插入指定产品上一次连续失败次数
     * @param apiId
     * @param fc
     */
    public static void insertApiMonitor(Integer apiId,Integer fc){
        SqlSession session = masterSqlSession();
        String insertApiMonitor = "org.qydata.mapper.ApiBanMapper.insertApiMonitor";
        Map<String, Object> insertParam = new HashMap<>();
        insertParam.put("apiId", apiId);
        insertParam.put("lastFc", fc);
        session.insert(insertApiMonitor, insertParam);
        session.commit();
        session.close();
    }

    public static void updateApiMonitor(Integer apiId,Integer fc){
        SqlSession session = masterSqlSession();
        String updateApiMonitor = "org.qydata.mapper.ApiBanMapper.updateApiMonitor";
        Map<String, Object> updateParam = new HashMap<>();
        updateParam.put("apiId", apiId);
        updateParam.put("lastFc", fc);
        session.update(updateApiMonitor, updateParam);
        session.commit();
        session.close();
    }

}
