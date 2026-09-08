/**
 * AI Agent 聊天页面（原生微信小程序页面）
 *
 * 说明：本项目是 uni-app 编译产物，此处以「原生 Page」方式新增页面，
 *       token 复用 uni-app 的 Vuex store.state.token，接口复用现有 baseUrl + authentication 约定。
 *       页面只负责：状态、消息展示、用户操作；底层请求/SSE 解析在 agent-api.js 中。
 */

var api = require('./agent-api.js');

// 将一行文本按 ** 拆分为 [{text, bold}] 段，用于轻量 Markdown 加粗
function splitBold(line) {
  var parts = String(line || '').split('**');
  var segments = [];
  for (var i = 0; i < parts.length; i++) {
    if (parts[i] === '') continue;
    segments.push({ text: parts[i], bold: (i % 2 === 1) });
  }
  return segments;
}

// 计算状态栏高度(px)：自定义导航栏下，顶栏需要下移避开状态栏
function getStatusBarHeight() {
  try {
    var win = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
    return win.statusBarHeight || 0;
  } catch (e) {
    return 0;
  }
}

Page({
  data: {
    sessions: [],            // 历史会话列表 [{sessionId, title}]
    currentSessionId: null,  // 当前会话 id
    currentTitle: '新聊天',  // 当前会话标题
    messages: [],            // 消息列表 [{role, content, lines}]
    inputText: '',           // 输入框内容
    loading: false,          // AI 是否正在生成
    hasToken: false,         // 是否已登录（拿到 token）
    scrollAnchor: 'anchorA', // 滚动到底部锚点
    statusBarHeight: getStatusBarHeight() // 状态栏高度(px)，用于自定义导航栏顶部内边距
  },

  _anchorFlip: false, // 锚点交替标志，用于强制 scroll-view 重新滚动到底部

  onLoad: function () {
    var token = api.getToken();
    this.setData({ hasToken: !!token });
    if (!token) {
      wx.showToast({ title: '请先登录后再使用 AI 助手', icon: 'none' });
      return;
    }
    this.loadSessions();
  },

  // 自定义导航栏返回
  goBack: function () {
    if (getCurrentPages().length > 1) {
      wx.navigateBack();
    } else {
      wx.navigateTo({ url: '/pages/index/index' });
    }
  },

  // 滚动到底部：交替使用两个底部锚点，强制 scroll-view 重新定位
  scrollToBottom: function () {
    this._anchorFlip = !this._anchorFlip;
    this.setData({ scrollAnchor: this._anchorFlip ? 'anchorA' : 'anchorB' });
  },

  // 加载历史会话列表
  loadSessions: function () {
    var that = this;
    api.getAgentSessions().then(function (res) {
      var list = Array.isArray(res) ? res : (res && Array.isArray(res.data) ? res.data : []);
      that.setData({ sessions: list });
    }).catch(function (err) {
      console.error('加载会话列表失败', err);
    });
  },

  // 新建聊天（不立即调用后端创建 session，第一次真正发消息时才创建）
  onNewChat: function () {
    this.setData({
      currentSessionId: null,
      currentTitle: '新聊天',
      messages: [],
      inputText: '',
      loading: false
    });
  },

  // 点击历史会话：加载历史消息
  onSelectSession: function (e) {
    if (this.data.loading) return; // 生成中不允许切换
    var id = Number(e.currentTarget.dataset.id);
    var title = e.currentTarget.dataset.title || '';
    var that = this;
    this.setData({ currentSessionId: id, currentTitle: title, messages: [] });
    api.getAgentHistory(id).then(function (res) {
      var list = (res && Array.isArray(res.data)) ? res.data : (Array.isArray(res) ? res : []);
      that.setData({ messages: that.decorateMessages(list) });
      that.scrollToBottom();
    }).catch(function (err) {
      console.error('加载历史消息失败', err);
      wx.showToast({ title: '加载历史消息失败', icon: 'none' });
    });
  },

  // 长按会话：重命名 / 删除
  onSessionActions: function (e) {
    var id = Number(e.currentTarget.dataset.id);
    var title = e.currentTarget.dataset.title || '';
    var that = this;
    wx.showActionSheet({
      itemList: ['重命名', '删除会话'],
      success: function (res) {
        if (res.tapIndex === 0) {
          that.renameSession(id, title);
        } else if (res.tapIndex === 1) {
          that.confirmDeleteSession(id);
        }
      }
    });
  },

  // 重命名会话（使用原生可输入弹窗）
  renameSession: function (id, title) {
    var that = this;
    wx.showModal({
      title: '重命名会话',
      editable: true,
      placeholderText: title,
      success: function (res) {
        if (res.confirm && res.content && res.content.trim()) {
          var newTitle = res.content.trim();
          api.updateSessionTitle(id, newTitle).then(function () {
            that.updateLocalTitle(id, newTitle);
          }).catch(function () {
            wx.showToast({ title: '重命名失败', icon: 'none' });
          });
        }
      }
    });
  },

  // 本地更新会话 title（同时更新当前标题与左侧列表）
  updateLocalTitle: function (id, title) {
    var sessions = this.data.sessions.map(function (s) {
      if (s.sessionId === id) { return { sessionId: id, title: title }; }
      return s;
    });
    var patch = { sessions: sessions };
    if (this.data.currentSessionId === id) { patch.currentTitle = title; }
    this.setData(patch);
  },

  // 删除会话确认
  confirmDeleteSession: function (id) {
    var that = this;
    wx.showModal({
      title: '提示',
      content: '确定删除该会话吗？删除后不可恢复。',
      success: function (res) {
        if (res.confirm) {
          api.deleteSession(id).then(function () {
            that.afterDelete(id);
          }).catch(function () {
            wx.showToast({ title: '删除失败', icon: 'none' });
          });
        }
      }
    });
  },

  // 删除成功后的本地处理
  afterDelete: function (id) {
    var sessions = this.data.sessions.filter(function (s) { return s.sessionId !== id; });
    var patch = { sessions: sessions };
    if (this.data.currentSessionId === id) {
      // 删除的是当前会话：回到「新聊天」状态
      patch.currentSessionId = null;
      patch.currentTitle = '新聊天';
      patch.messages = [];
      patch.loading = false;
    }
    this.setData(patch);
  },

  // 输入框内容变化
  onInput: function (e) {
    this.setData({ inputText: e.detail.value });
  },

  // 发送消息
  onSend: function () {
    var text = (this.data.inputText || '').trim();
    if (!text) return;
    if (this.data.loading) return; // 防止重复发送
    if (!this.data.hasToken) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }

    var that = this;
    // 1. 立即添加 USER 消息
    // 2. 立即添加空 Assistant 消息（后续 token 始终追加到同一个气泡）
    var messages = this.data.messages.slice();
    messages.push({ role: 'USER', content: text, lines: null });
    messages.push({ role: 'ASSISTANT', content: '', lines: [{ segments: [] }] });
    var assistantIndex = messages.length - 1;

    this.setData({ messages: messages, inputText: '', loading: true });
    this.scrollToBottom();

    // 3. 发起 SSE 流式请求（新会话不传 sessionId，由后端自动创建）
    api.streamAgentChat({
      sessionId: this.data.currentSessionId,
      message: text,
      onEvent: function (evt) { that.handleSSEEvent(evt, assistantIndex); },
      onEnd: function () {
        if (that.data.loading) {
          that.setData({ loading: false });
          that.scrollToBottom();
        }
      },
      onError: function (err) {
        console.error('SSE 请求失败', err);
        that.setData({ loading: false });
        var assistant = that.data.messages[assistantIndex];
        if (assistant && !assistant.content) {
          // 401 表示登录失效，给出明确提示；其余情况给通用失败提示
          var failText = (err && err.statusCode === 401) ? '（登录已失效，请重新登录）' : '（生成失败，请稍后重试）';
          that.updateAssistantContent(assistantIndex, failText);
        }
        var tip = (err && err.statusCode === 401) ? '登录已失效，请重新登录' : '生成失败，请重试';
        wx.showToast({ title: tip, icon: 'none' });
      }
    });
  },

  // 处理单个 SSE 事件
  handleSSEEvent: function (evt, assistantIndex) {
    if (!evt || !evt.type) return;
    switch (evt.type) {
      case 'meta':
        // 新会话首次请求返回 sessionId，前端保存；threadId 仅保存，业务主要用 sessionId
        if (evt.sessionId !== null && evt.sessionId !== undefined) {
          var patch = {};
          if (this.data.currentSessionId === null || this.data.currentSessionId === undefined) {
            patch.currentSessionId = evt.sessionId;
          }
          this._threadId = evt.threadId;
          this.setData(patch);
        }
        break;
      case 'token':
        // 不断追加到同一个 Assistant 气泡，不新建消息
        this.appendAssistantContent(assistantIndex, evt.content || '');
        break;
      case 'title':
        // 新会话第一轮自动生成的标题：更新当前标题，并更新左侧会话列表
        if (evt.title) {
          this.setData({ currentTitle: evt.title });
          if (this.data.currentSessionId !== null && this.data.currentSessionId !== undefined) {
            this.updateLocalTitle(this.data.currentSessionId, evt.title);
          }
        }
        break;
      case 'done':
        // 本轮回答完成：停止 loading，恢复输入框，刷新左侧列表
        this.setData({ loading: false });
        this.scrollToBottom();
        this.loadSessions();
        break;
      case 'error':
        // 收到 error 事件：立即结束“生成中”状态，不再等待 done
        this.setData({ loading: false });
        // 整体替换当前 assistant 消息（空占位或已流式的残缺内容）为统一错误提示
        this.updateAssistantContent(assistantIndex, evt.message || '智能助手暂时不可用，请稍后再试');
        this.scrollToBottom();
        break;
      default:
        break;
    }
  },

  // 追加 token 到指定 assistant 消息（content + lines 一并更新，并滚动到底部）
  appendAssistantContent: function (index, text) {
    var newContent = (this.data.messages[index].content || '') + text;
    this._anchorFlip = !this._anchorFlip;
    var patch = {};
    patch['messages[' + index + '].content'] = newContent;
    patch['messages[' + index + '].lines'] = this.buildLines(newContent);
    patch['scrollAnchor'] = this._anchorFlip ? 'anchorA' : 'anchorB';
    this.setData(patch);
  },

  // 覆盖 assistant 消息内容（用于错误兜底）
  updateAssistantContent: function (index, text) {
    var patch = {};
    patch['messages[' + index + '].content'] = text;
    patch['messages[' + index + '].lines'] = this.buildLines(text);
    this.setData(patch);
  },

  // 历史消息装饰：把后端 raw 消息转为可渲染结构
  decorateMessages: function (rawList) {
    var that = this;
    var list = Array.isArray(rawList) ? rawList : [];
    return list.map(function (m) {
      var role = (m.role || 'USER').toUpperCase();
      return {
        role: role,
        content: m.content || '',
        lines: role === 'ASSISTANT' ? that.buildLines(m.content || '') : null
      };
    });
  },

  // 把文本按换行拆成行，每行按 ** 拆成加粗段
  buildLines: function (text) {
    return String(text || '').split('\n').map(function (line) {
      return { segments: splitBold(line) };
    });
  }
});
