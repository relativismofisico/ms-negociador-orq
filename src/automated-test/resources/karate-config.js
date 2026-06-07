/**
 * karate-config.js — Configuracion central multi-ambiente.
 * Activa el ambiente via: AMBIENTE_PIPE=qa ./gradlew smokeTest
 * O via propiedad Gradle: ./gradlew smokeTest -Penv=qa
 * El task de Gradle propaga el ambiente como system property 'karate.env'.
 */
function fn() {
    var env = karate.env || 'dev';
    karate.log('[karate-config] ambiente:', env);

    var System = Java.type('java.lang.System');

    var getenv = function(key, def) {
        var v = System.getenv(key);
        return (v !== null) ? ('' + v) : (def || '');
    };

    // ─── Configuracion base ─────────────────────────────────────
    var baseUrl   = getenv('BASE_URL', 'http://localhost:8083');
    var jwtSecret = getenv('JWT_SECRET', '');

    var testData = {
        rutEmpresa:    getenv('TEST_RUT_EMPRESA',   '800111222-9'),
        rutOperador:   getenv('TEST_RUT_OPERADOR',  '12345678-9'),
        rutFondeador:  getenv('TEST_RUT_FONDEADOR', '700999888-5'),
        rutPagador:    getenv('TEST_RUT_PAGADOR',   '900333444-1'),
        rutProveedor:  getenv('TEST_RUT_PROVEEDOR', '800111222-9'),
        negociacionId: getenv('TEST_NEG_ID',        ''),
        numeroFactura: getenv('TEST_FACTURA_NUM',   'FAC-TEST-001'),
        fechaProceso:  getenv('TEST_FECHA_PROCESO', '2024-01-15')
    };

    var tokenAdmin     = '';
    var tokenEmpresa   = '';
    var tokenFondeador = '';
    var tokenOperario  = '';
    var tokenExpired   = '';
    var tokenBadRol    = '';

    // ─── Configuracion por ambiente ──────────────────────────────
    if (env === 'dev') {
        if (!jwtSecret) {
            jwtSecret = 'AF84F1FGllNpNnLG055fdg5hGHJK4KGG5VH5TR5J05JFGGDFDGXVV545J4505G666JFGF2mMY95y';
        }
        var JwtHelper = Java.type('com.negociadororq.auth.JwtTestHelper');
        tokenAdmin     = JwtHelper.generate('dev-admin',     'ADMINISTRADOR', jwtSecret);
        tokenEmpresa   = JwtHelper.generate('dev-empresa',   'EMPRESA',       jwtSecret);
        tokenFondeador = JwtHelper.generate('dev-fondeador', 'FONDEADOR',     jwtSecret);
        tokenOperario  = JwtHelper.generate('dev-operario',  'OPERARIO',      jwtSecret);
        tokenExpired   = JwtHelper.generateExpired('dev-exp', 'ADMINISTRADOR', jwtSecret);
        tokenBadRol    = JwtHelper.generate('dev-bad',       'ROL_INVALIDO',  jwtSecret);
        karate.log('[config-dev] baseUrl:', baseUrl);

    } else if (env === 'qa') {
        if (!jwtSecret) {
            throw new Error('[config-qa] JWT_SECRET no esta definido.');
        }
        var JwtHelperQa = Java.type('com.negociadororq.auth.JwtTestHelper');
        tokenAdmin     = JwtHelperQa.generate('qa-admin',     'ADMINISTRADOR', jwtSecret);
        tokenEmpresa   = JwtHelperQa.generate('qa-empresa',   'EMPRESA',       jwtSecret);
        tokenFondeador = JwtHelperQa.generate('qa-fondeador', 'FONDEADOR',     jwtSecret);
        tokenOperario  = JwtHelperQa.generate('qa-operario',  'OPERARIO',      jwtSecret);
        tokenExpired   = JwtHelperQa.generateExpired('qa-exp', 'ADMINISTRADOR', jwtSecret);
        tokenBadRol    = JwtHelperQa.generate('qa-bad',       'ROL_INVALIDO',  jwtSecret);
        karate.log('[config-qa] baseUrl:', baseUrl);

    } else if (env === 'prod') {
        // En produccion los tokens vienen pre-obtenidos; nunca generados aqui
        tokenAdmin     = getenv('TEST_TOKEN_ADMIN',     '');
        tokenEmpresa   = getenv('TEST_TOKEN_EMPRESA',   '');
        tokenFondeador = getenv('TEST_TOKEN_FONDEADOR', '');
        tokenOperario  = getenv('TEST_TOKEN_OPERARIO',  '');
        jwtSecret      = '';
        tokenExpired   = '';
        tokenBadRol    = '';
        karate.configure('readTimeout', 20000);
        karate.log('[config-prod] baseUrl:', baseUrl);
    }

    karate.configure('connectTimeout', 5000);
    karate.configure('readTimeout',    10000);

    return {
        env:            env,
        baseUrl:        baseUrl,
        jwtSecret:      jwtSecret,
        tokenAdmin:     tokenAdmin,
        tokenEmpresa:   tokenEmpresa,
        tokenFondeador: tokenFondeador,
        tokenOperario:  tokenOperario,
        tokenExpired:   tokenExpired,
        tokenBadRol:    tokenBadRol,
        testData:       testData
    };
}