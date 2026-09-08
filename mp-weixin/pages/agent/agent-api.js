/**
 * AI Agent 聊天相关 API 封装（原生小程序模块）
 *
 * 说明：
 * 1. 本页面是「原生微信小程序」页面，与 uni-app 编译页面共存。
 * 2. 复用现有后端的 baseUrl（http://localhost:8080）与 JWT 鉴权方式。
 *    现有 utils/request.js 的请求头为小写 authentication，这里保持一致，不另建鉴权机制。
 * 3. SSE 接口是 POST + JSON Body + JWT，小程序没有 fetch/ReadableStream/EventSource，
 *    因此使用 wx.request 的「分块传输 enableChunked + onChunkReceived」实现等价流式解析。
 */

// 后端地址：必须与 common/vendor.js 内 utils/env.js 的 baseUrl 保持一致（vendor.js 第 20509 行）。
// 说明：utils/env.js 已编译进 common/vendor.js，磁盘上不存在独立的 utils/env.js 文件，
//      原生页面无法 require 它，因此这里作为唯一的镜像常量声明，值需与其完全相同。
var BASE_URL = 'http://localhost:8080';

/**
 * 获取登录 token
 * 本项目 token 保存在 uni-app 的 Vuex store.state.token（内存态），通过 getApp().$vm 访问；
 * 同时做本地 storage 兜底。
 */
function getToken() {
  try {
    var app = getApp();
    if (app && app.$vm && app.$vm.$store && app.$vm.$store.state) {
      return app.$vm.$store.state.token || '';
    }
    if (app && app.$store && app.$store.state) {
      return app.$store.state.token || '';
    }
  } catch (e) {}
  return wx.getStorageSync('token') || wx.getStorageSync('authentication') || '';
}

/**
 * 构造请求头
 * 与现有 request.js 保持一致：用 authentication 承载 JWT；额外支持覆盖 Accept 等。
 */
function buildHeader(extra) {
  var header = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'authentication': getToken()
  };
  if (extra) {
    for (var k in extra) {
      header[k] = extra[k];
    }
  }
  return header;
}

/**
 * 简单 REST 请求 Promise 封装（用于会话列表 / 历史 / 改标题 / 删除）
 */
function request(url, method, data) {
  return new Promise(function (resolve, reject) {
    wx.request({
      url: BASE_URL + url,
      method: method || 'GET',
      data: data || {},
      header: buildHeader(),
      success: function (res) {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data);
        } else {
          reject(res.data || { message: '请求失败，状态码 ' + res.statusCode });
        }
      },
      fail: function (err) {
        reject({ message: '网络异常', err: err });
      }
    });
  });
}

/**
 * 获取当前用户的历史会话列表
 * GET /user/agent/sessions
 */
function getAgentSessions() {
  return request('/user/agent/sessions', 'GET');
}

/**
 * 获取某个会话的历史消息
 * GET /user/agent/history/{sessionId}
 */
function getAgentHistory(sessionId) {
  return request('/user/agent/history/' + sessionId, 'GET');
}

/**
 * 修改会话标题
 * PATCH /user/agent/session/{sessionId}/title
 */
function updateSessionTitle(sessionId, title) {
  return request('/user/agent/session/' + sessionId + '/title', 'PATCH', { title: title });
}

/**
 * 删除会话
 * DELETE /user/agent/session/{sessionId}
 */
function deleteSession(sessionId) {
  return request('/user/agent/session/' + sessionId, 'DELETE');
}

/**
 * SSE 流式聊天
 * POST /user/agent/chat/stream
 *
 * 使用 wx.request 分块传输 + onChunkReceived 接收流式数据，
 * 并通过 buffer 正确处理 TCP 拆包，再按 \r?\n\r?\n 拆分 SSE 事件。
 *
 * @param {Object} options
 *   - sessionId: 会话 id（新会话可不传，后端自动创建）
 *   - message:   用户消息
 *   - onEvent:   每解析出一个 SSE 事件对象时回调
 *   - onEnd:     请求正常结束（done 后连接关闭）
 *   - onError:   请求失败回调
 * @returns wx.request 返回的 requestTask
 */
function streamAgentChat(options) {
  var body = { message: options.message };
  if (options.sessionId !== null && options.sessionId !== undefined && options.sessionId !== '') {
    body.sessionId = options.sessionId;
  }

  var buffer = ''; // 累积不完整的分块内容

  var requestTask = wx.request({
    url: BASE_URL + '/user/agent/chat/stream',
    method: 'POST',
    data: body,
    enableChunked: true, // 关键：开启分块传输以接收 SSE 流
    header: buildHeader({ 'Accept': 'text/event-stream' }),
    success: function (res) {
      // 非 2xx（如 401 未授权、500 等）也走 success 回调，需按状态码判定
      if (res.statusCode >= 200 && res.statusCode < 300) {
        // SSE 在 done 之后正常断开连接，success 在此触发
        flushBuffer();
        if (options.onEnd) options.onEnd();
      } else {
        if (options.onError) options.onError({ statusCode: res.statusCode, data: res.data });
      }
    },
    fail: function (err) {
      // 网络级错误（连接失败、超时等）
      if (options.onError) options.onError(err);
    }
  });

  // 监听分块数据
  if (requestTask && requestTask.onChunkReceived) {
    requestTask.onChunkReceived(function (chunk) {
      buffer += decodeChunk(chunk.data);
      processBuffer();
    });
  }

  // 处理 buffer 中已完整的 SSE 事件
  function processBuffer() {
    // 按 \r?\n\r?\n 拆分，最后一段不完整内容继续保留在 buffer 中
    var events = buffer.split(/\r?\n\r?\n/);
    buffer = events.pop();
    for (var i = 0; i < events.length; i++) {
      var evt = parseSSEEvent(events[i]);
      if (evt && options.onEvent) options.onEvent(evt);
    }
  }

  // 请求结束时，若 buffer 还残留最后一段，尝试解析一次
  function flushBuffer() {
    if (buffer) {
      var evt = parseSSEEvent(buffer);
      buffer = '';
      if (evt && options.onEvent) options.onEvent(evt);
    }
  }

  return requestTask;
}

/**
 * 将分块数据（ArrayBuffer / TypedArray / string）解码为 UTF-8 字符串
 */
function decodeChunk(chunk) {
  if (!chunk) return '';
  if (typeof chunk === 'string') return chunk;
  var bytes = (chunk instanceof ArrayBuffer) ? new Uint8Array(chunk) : chunk;
  if (typeof TextDecoder !== 'undefined') {
    try {
      return new TextDecoder('utf-8').decode(bytes);
    } catch (e) {}
  }
  return utf8ArrayToString(bytes);
}

/**
 * 手动 UTF-8 解码（TextDecoder 不可用时的兜底）
 */
function utf8ArrayToString(bytes) {
  var str = '';
  var i = 0;
  var len = bytes.length;
  while (i < len) {
    var c = bytes[i++];
    if (c < 0x80) {
      str += String.fromCharCode(c);
    } else if (c < 0xe0) {
      str += String.fromCharCode(((c & 0x1f) << 6) | (bytes[i++] & 0x3f));
    } else if (c < 0xf0) {
      str += String.fromCharCode(((c & 0x0f) << 12) | ((bytes[i++] & 0x3f) << 6) | (bytes[i++] & 0x3f));
    } else {
      var code = ((c & 0x07) << 18) | ((bytes[i++] & 0x3f) << 12) | ((bytes[i++] & 0x3f) << 6) | (bytes[i++] & 0x3f);
      code -= 0x10000;
      str += String.fromCharCode(0xd800 + (code >> 10), 0xdc00 + (code & 0x3ff));
    }
  }
  return str;
}

/**
 * 解析单个 SSE 事件文本，提取 data 字段并 JSON.parse
 * 兼容事件内可能的多行 data（用 \n 连接）
 */
function parseSSEEvent(raw) {
  if (!raw) return null;
  var dataLines = [];
  var lines = raw.split(/\r?\n/);
  for (var i = 0; i < lines.length; i++) {
    var trimmed = lines[i].trim();
    if (trimmed.indexOf('data:') === 0) {
      dataLines.push(trimmed.slice(5).trim());
    }
  }
  if (dataLines.length === 0) return null;
  try {
    return JSON.parse(dataLines.join('\n'));
  } catch (e) {
    return null;
  }
}

module.exports = {
  BASE_URL: BASE_URL,
  getToken: getToken,
  getAgentSessions: getAgentSessions,
  getAgentHistory: getAgentHistory,
  updateSessionTitle: updateSessionTitle,
  deleteSession: deleteSession,
  streamAgentChat: streamAgentChat
};
