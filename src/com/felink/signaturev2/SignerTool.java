package com.felink.signaturev2;

import com.android.apksigner.ApkSignerTool;
import com.felink.signaturev2.domain.KeyStore;
import com.felink.signaturev2.kitset.FileUtil;
import com.felink.signaturev2.kitset.StringUtil;
import com.felink.signaturev2.platform.PlatformDor;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class SignerTool {

    public static final String TEMP_DIR = new File("." + File.separator + "sign_tmp" + File.separator).getAbsolutePath();

    private static PrintStream consoleMonitor;

    static {
        try {
            consoleMonitor = new Monitor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static PrintStream sourceOut = System.out;
    private static PrintStream sourceErr = System.err;

    private static void monitor() {
        System.setOut(consoleMonitor);
        System.setErr(consoleMonitor);
    }

    private static void unmonitor() {
        System.setOut(sourceOut);
        System.setErr(sourceErr);
        consoleMonitor.close();
    }

    public static boolean sign(String[] params) {

        boolean result = false;
        if (params != null && params.length > 0) {
            String inputPath = null, outputPath = null;
            KeyStore keyStore = new KeyStore();
            List<String> extras = new ArrayList<>();
            boolean v1 = true, v2 = true;
            for (int i = 0, len = params.length; i < len; i++) {
                String opt = params[i];
                if ("-storepass".equals(opt)) {
                    keyStore.pass = params[++i];
                } else if ("-keypass".equals(opt)) {
                    keyStore.keyPass = params[++i];
                } else if ("-keystore".equals(opt)) {
                    keyStore.path = params[++i];
                } else if ("-signedjar".equals(opt)) {
                    outputPath = params[++i];
                    inputPath = params[++i];
                    keyStore.keyAlias = params[++i];
                } else if ("-v1-signing-enabled".equals(opt)) {
                    try {
                        v1 = Boolean.valueOf(params[i++]);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if ("-v2-signing-enabled".equals(opt)) {
                    try {
                        v2 = Boolean.valueOf(params[i++]);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    extras.add(opt);
                }
            }
            result = sign(inputPath, outputPath, keyStore, v1, v2, false, extras);
        }
        return result;
    }

    /**
     * 签名（v1、v2一般填true，zipalign填falase）
     *
     * @param inputpath  待签名APK文件
     * @param outputpath 已签名apk文件
     * @param keyStore   keystore对象
     * @param v1         是否v1签名
     * @param v2         是否v2签名
     * @param zipalign   是否进行zipalign
     * @param extras     额外参数
     * @return
     */
    public static boolean sign(final String inputpath, final String outputpath, KeyStore keyStore, boolean v1, boolean v2, boolean zipalign, List<String> extras) {
        monitor();
        boolean result = false;
        delAllTmp();

        FileUtil.createDir(TEMP_DIR);
        String finalOutputPath = new File(TEMP_DIR, FileUtil.getFileName(outputpath)).getAbsolutePath();
        String finalInputPath = new File(TEMP_DIR, FileUtil.getFileName(inputpath)).getAbsolutePath();

        try {
            if (!FileUtil.copy(inputpath, finalInputPath)) {
                finalInputPath = inputpath;
            }
            if (zipalign) {
                try {
                    String aligned = PlatformDor.zipalign(finalInputPath);
                    if (!StringUtil.isEmpty(aligned) && FileUtil.isExists(aligned)) {
                        finalInputPath = aligned;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            try {
                if (v2) {//v2签名直接用apksignerv2.jar
                    finalOutputPath = finalInputPath + "_signed" + (v1 ? "v1v2" : "v2");
                    result = SignerV2.sign(finalInputPath, finalOutputPath, keyStore, v1, v2, false);
                    System.out.println("Sign V2 " + (result ? "Successful" : "Failure"));
                } else if (v1) {//只要v1签名的，仍用jarsigner.exe
                    finalOutputPath = finalInputPath + "_signedv1";
                    result = SignerV1.sign(finalInputPath, finalOutputPath, keyStore, extras);
                    System.out.println("Sign V1 " + (result ? "Successful" : "Failure"));
                }
                //zipalign校验
                result = PlatformDor.checkZipaligned(finalOutputPath);
                if (result) {
                    //签名结果校验
                    SignerTool.verify(finalOutputPath);
                    String logging = FileUtil.readFileContent(Monitor.file.getAbsolutePath());
                    if (v1) {
                        result = result && logging.contains("Verified using v1 scheme (JAR signing): true");
                    }
                    if (v2) {
                        result = result && logging.contains("Verified using v2 scheme (APK Signature Scheme v2): true");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (!result) {
                    FileUtil.delFile(outputpath);
                } else {
                    FileUtil.copy(finalOutputPath, outputpath);
                }
            }
        } finally {
            System.out.println("[" + v1 + ", " + v2 + "] Signed result: " + (result ? "Successful" : "Failure"));
            unmonitor();
        }
        return result;
    }

    static void delAllTmp() {
        FileUtil.delAllFile(TEMP_DIR);
    }


    public static void verify(String path) throws Exception {
        String[] verifyparams = {"verify", "-v", path};
        ApkSignerTool.main(verifyparams);
    }
}
