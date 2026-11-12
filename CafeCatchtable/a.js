/* =========================================================
   카페 캐치테이블 공통 스크립트
   - API 호출(fetch) + 오류 코드 → 한글 메시지 변환 (요구사항 4.3, API 명세 1장)
   - 로그인 확인 / 점주 권한 확인 / 헤더 렌더링
   - 날짜·상태 표시 도우미
   모든 페이지 스크립트는 App.page(async (me) => { ... }) 안에서 실행합니다.
   ========================================================= */
(function () {
  'use strict';

  const API_BASE = '/api';

  /** 요구사항 명세서 4.3 오류 코드 → 화면 메시지 */
  const DB_MESSAGES = {
    '-20001': '영업시간 안에서만 예약할 수 있습니다',
    '-20002': '오늘부터 7일 이내만 예약할 수 있습니다',
    '-20003': '이 좌석의 적정인원을 넘었습니다',
    '-20004': '이미 예약된 시간입니다',
    '-20005': '같은 시간에 다른 예약이 있습니다',
    '-20006': '입실 10분 전부터 15분 후까지 체크인할 수 있습니다',
    '-20007': '지금은 처리할 수 없는 예약입니다',
    '-20008': '본인 예약만 처리할 수 있습니다',
    '-20009': '현재 운영하지 않는 좌석입니다',
    '-20010': '예약이 남아 있는 좌석입니다'
  };

  /** API 명세서 1장 code → 기본 메시지 (서버 message가 없을 때 사용) */
  const CODE_MESSAGES = {
    VALIDATION_FAILED: '입력값을 다시 확인해 주세요',
    UNAUTHORIZED: '로그인이 필요합니다',
    FORBIDDEN: '접근 권한이 없습니다',
    NOT_OWNER_OF_RESV: DB_MESSAGES['-20008'],
    NOT_FOUND: '요청한 정보를 찾을 수 없습니다',
    DUPLICATE_ID: '이미 사용 중인 아이디입니다',
    DUPLICATE_BUSINESS_NUM: '이미 등록된 사업자등록번호입니다',
    DUPLICATE_SEAT_NUM: '이 구역에 같은 좌석번호가 이미 있습니다',
    RESV_TIME_CONFLICT: DB_MESSAGES['-20004'],
    MEMBER_TIME_CONFLICT: DB_MESSAGES['-20005'],
    SEAT_HAS_RESERVATION: DB_MESSAGES['-20010'],
    OUT_OF_BUSINESS_HOURS: DB_MESSAGES['-20001'],
    OUT_OF_BOOKING_RANGE: DB_MESSAGES['-20002'],
    OVER_CAPACITY: DB_MESSAGES['-20003'],
    CHECKIN_WINDOW_CLOSED: DB_MESSAGES['-20006'],
    INVALID_STATE: DB_MESSAGES['-20007'],
    SEAT_DISABLED: DB_MESSAGES['-20009']
  };

  const STATUS_MESSAGES = {
    0: '서버에 연결할 수 없습니다. 네트워크를 확인해 주세요',
    400: CODE_MESSAGES.VALIDATION_FAILED,
    401: CODE_MESSAGES.UNAUTHORIZED,
    403: CODE_MESSAGES.FORBIDDEN,
    404: CODE_MESSAGES.NOT_FOUND
  };

  class ApiError extends Error {
    constructor(status, body) {
      super(resolveMessage(status, body));
      this.status = status;
      this.code = body && body.code;
      this.dbCode = body && body.dbCode;
    }
  }

  function resolveMessage(status, body) {
    if (body && body.dbCode != null && DB_MESSAGES[String(body.dbCode)]) return DB_MESSAGES[String(body.dbCode)];
    if (body && body.message) return body.message;
    if (body && CODE_MESSAGES[body.code]) return CODE_MESSAGES[body.code];
    return STATUS_MESSAGES[status] || '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요';
  }

  /**
   * API 호출. 성공 시 JSON(204면 null), 실패 시 ApiError를 던집니다.
   * @param {string} path  '/cafes' 처럼 /api 뒤의 경로
   * @param {{method?:string, body?:any, query?:Object}} [opts]
   */
  async function api(path, opts) {
    const o = opts || {};
    let url = API_BASE + path;
    if (o.query) {
      const qs = new URLSearchParams();
      Object.keys(o.query).forEach(function (k) {
        const v = o.query[k];
        if (v !== undefined && v !== null && v !== '') qs.append(k, v);
      });
      const s = qs.toString();
      if (s) url += '?' + s;
    }
    const init = {
      method: o.method || 'GET',
      credentials: 'same-origin',
      headers: { 'Accept': 'application/json' }
    };
    if (o.body !== undefined) {
      init.headers['Content-Type'] = 'application/json; charset=UTF-8';
      init.body = JSON.stringify(o.body);
    }
    let res;
    try {
      res = await fetch(url, init);
    } catch (e) {
      throw new ApiError(0, null);
    }
    if (res.status === 204) return null;
    const text = await res.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch (e) { data = null; }
    }
    if (!res.ok) throw new ApiError(res.status, data);
    return data;
  }

  /* ---------------- 인증·권한 ---------------- */
  let mePromise = null;
  function loadMe(force) {
    if (!mePromise || force) {
      mePromise = api('/auth/me').catch(function (e) {
        if (e.status === 401) return null;
        throw e;
      });
    }
    return mePromise;
  }

  function renderHeader(me) {
    const role = me ? me.role : null;
    document.body.classList.toggle('auth-guest', !me);
    document.body.classList.toggle('auth-owner', role === 'OWNER');

    document.querySelectorAll('[data-show]').forEach(function (el) {
      const want = el.dataset.show.split(/\s+/);
      let visible = false;
      if (want.indexOf('guest') >= 0 && !me) visible = true;
      if (want.indexOf('member') >= 0 && me) visible = true;
      if (want.indexOf('owner') >= 0 && role === 'OWNER') visible = true;
      if (want.indexOf('user-only') >= 0 && role === 'USER') visible = true;
      el.hidden = !visible;
    });

    const nameSlot = document.querySelector('[data-slot="user-name"]');
    if (nameSlot && me) {
      nameSlot.innerHTML = '<strong>' + esc(me.name) + '</strong>님' +
        (role === 'OWNER' ? ' <span class="badge badge--owner">점주</span>' : '');
    }

    const path = location.pathname;
    document.querySelectorAll('.nav a').forEach(function (a) {
      const href = a.getAttribute('href');
      a.classList.toggle('is-active', path === href || (href !== '/' && path.indexOf(href + '/') === 0));
    });

    const logoutBtn = document.querySelector('[data-action="logout"]');
    if (logoutBtn && !logoutBtn.dataset.bound) {
      logoutBtn.dataset.bound = '1';
      logoutBtn.addEventListener('click', async function () {
        try { await api('/auth/logout', { method: 'POST' }); } catch (e) { /* 세션이 이미 만료돼도 로그인 화면으로 */ }
        location.href = '/login';
      });
    }
  }

  function showForbidden() {
    const main = document.querySelector('main');
    if (!main) return;
    main.innerHTML =
      '<div class="container container--narrow"><div class="card stack" style="text-align:center">' +
      '<h1>403</h1><p class="muted">점주만 사용할 수 있는 화면입니다.<br>사업자등록번호로 점주 신청을 먼저 해 주세요.</p>' +
      '<div class="row" style="justify-content:center"><a class="btn" href="/owner/apply">점주 신청</a>' +
      '<a class="btn btn--ghost" href="/cafes">카페 찾기</a></div></div></div>';
  }

  /**
   * 페이지 진입점. body의 data-auth 값으로 접근을 제어합니다.
   *  - public : 누구나 (로그인·회원가입)
   *  - user   : 로그인 필요 (기본값)
   *  - owner  : OWNER만
   */
  function page(fn) {
    const run = async function () {
      const mode = document.body.dataset.auth || 'user';
      let me;
      try {
        me = await loadMe();
      } catch (e) {
        renderHeader(null);
        toast(e.message, 'error');
        if (mode !== 'public') return;
        me = null;
      }
      renderHeader(me);
      if (mode !== 'public' && !me) {
        location.replace('/login?next=' + encodeURIComponent(location.pathname + location.search));
        return;
      }
      if (mode === 'owner' && me.role !== 'OWNER') {
        showForbidden();
        return;
      }
      try {
        await fn(me);
      } catch (e) {
        console.error(e);
        handleError(e);
      }
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run);
    else run();
  }

  /** 세션 만료(401)면 로그인으로, 나머지는 토스트로 */
  function handleError(e, target) {
    if (e && e.status === 401) {
      location.replace('/login?next=' + encodeURIComponent(location.pathname + location.search));
      return;
    }
    const msg = (e && e.message) || '오류가 발생했습니다';
    if (target) showAlert(target, msg);
    else toast(msg, 'error');
  }

  /* ---------------- UI 도우미 ---------------- */
  function toast(message, type) {
    let wrap = document.querySelector('.toast-wrap');
    if (!wrap) {
      wrap = document.createElement('div');
      wrap.className = 'toast-wrap';
      wrap.setAttribute('role', 'status');
      wrap.setAttribute('aria-live', 'polite');
      document.body.appendChild(wrap);
    }
    const t = document.createElement('div');
    t.className = 'toast' + (type ? ' toast--' + type : '');
    t.textContent = message;
    wrap.appendChild(t);
    setTimeout(function () { t.remove(); }, type === 'error' ? 4500 : 2800);
  }

  function showAlert(el, message, type) {
    if (typeof el === 'string') el = document.querySelector(el);
    if (!el) return;
    if (!message) { el.hidden = true; el.textContent = ''; return; }
    el.className = 'alert alert--' + (type || 'error');
    el.textContent = message;
    el.hidden = false;
  }

  function setBusy(btn, busy) {
    if (!btn) return;
    btn.disabled = !!busy;
    btn.classList.toggle('is-busy', !!busy);
  }

  function esc(v) {
    if (v === null || v === undefined) return '';
    return String(v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function query(name) {
    return new URLSearchParams(location.search).get(name);
  }

  /** /cafes/3/reserve → pathSegment(1) === '3' */
  function pathSegment(index) {
    return location.pathname.split('/').filter(Boolean)[index];
  }

  /* ---------------- 날짜·시간 ---------------- */
  const WEEK_KO = ['일', '월', '화', '수', '목', '금', '토'];
  const DAY_CODES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  const DAY_KO = { Mon: '월', Tue: '화', Wed: '수', Thu: '목', Fri: '금', Sat: '토', Sun: '일' };
  const JS_DAY_TO_CODE = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

  function pad(n) { return String(n).padStart(2, '0'); }

  /** 'yyyy-MM-ddTHH:mm:ss' (오프셋 없음) → 로컬 Date */
  function parseDt(s) {
    if (!s) return null;
    if (s instanceof Date) return s;
    const m = String(s).match(/^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?)?/);
    if (!m) return new Date(s);
    return new Date(+m[1], +m[2] - 1, +m[3], +(m[4] || 0), +(m[5] || 0), +(m[6] || 0));
  }
  /** Date → 'yyyy-MM-ddTHH:mm:ss' (API 형식) */
  function toApiDt(d) {
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
      'T' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
  }
  /** Date → 'yyyy-MM-dd' */
  function toApiDate(d) {
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }
  function addDays(d, n) { const r = new Date(d); r.setDate(r.getDate() + n); return r; }
  function addMinutes(d, n) { return new Date(d.getTime() + n * 60000); }
  function fmtTime(s) { const d = parseDt(s); return d ? pad(d.getHours()) + ':' + pad(d.getMinutes()) : ''; }
  function fmtDate(s) {
    const d = parseDt(s);
    return d ? (d.getMonth() + 1) + '월 ' + d.getDate() + '일 (' + WEEK_KO[d.getDay()] + ')' : '';
  }
  function fmtDateTime(s) { return s ? fmtDate(s) + ' ' + fmtTime(s) : ''; }
  function fmtRange(start, end) {
    const a = parseDt(start), b = parseDt(end);
    if (!a) return '';
    const sameDay = b && a.toDateString() === b.toDateString();
    return fmtDate(a) + ' ' + fmtTime(a) + ' ~ ' + (b ? (sameDay ? '' : fmtDate(b) + ' ') + fmtTime(b) : '');
  }
  function fmtMinutes(min) {
    min = Math.max(0, Math.round(min));
    const h = Math.floor(min / 60), m = min % 60;
    if (!h) return m + '분';
    return h + '시간' + (m ? ' ' + m + '분' : '');
  }
  /** 'Mon,Tue,...' → '월·화·…' (7일이면 '매일') */
  function fmtDays(days) {
    if (!days) return '-';
    const list = String(days).split(',').map(function (s) { return s.trim(); }).filter(Boolean);
    if (list.length === 7) return '매일';
    return DAY_CODES.filter(function (c) { return list.indexOf(c) >= 0; })
      .map(function (c) { return DAY_KO[c]; }).join('·');
  }
  function isOperatingDay(days, date) {
    if (!days) return true;
    return String(days).split(',').map(function (s) { return s.trim(); })
      .indexOf(JS_DAY_TO_CODE[date.getDay()]) >= 0;
  }
  /** 'HH:mm' 을 date 날짜에 붙인 Date. 24:00 은 23:59로 봄 (요구사항 6장) */
  function atTime(date, hhmm) {
    const p = String(hhmm || '00:00').split(':');
    let h = +p[0], m = +(p[1] || 0);
    if (h >= 24) { h = 23; m = 59; }
    const d = new Date(date);
    d.setHours(h, m, 0, 0);
    return d;
  }

  /* ---------------- 상태 배지 ---------------- */
  const STATUS_CLASS = {
    '빈자리': 'free', '예약됨': 'booked', '이용중': 'inuse', '사용중지': 'disabled',
    '예약완료': 'booked', '이용완료': 'done', '예약취소': 'cancel', '노쇼': 'noshow'
  };
  function statusClass(status) { return STATUS_CLASS[status] || 'disabled'; }
  function badge(status) {
    return '<span class="badge badge--' + statusClass(status) + '">' + esc(status) + '</span>';
  }

  /* ---------------- 입력 자동 하이픈 ---------------- */
  function bindPhoneFormat(input) {
    input.addEventListener('input', function () {
      const d = input.value.replace(/\D/g, '').slice(0, 11);
      input.value = d.length < 4 ? d : d.length < 8 ? d.slice(0, 3) + '-' + d.slice(3)
        : d.slice(0, 3) + '-' + d.slice(3, 7) + '-' + d.slice(7);
    });
  }
  function bindBizNumFormat(input) {
    input.addEventListener('input', function () {
      const d = input.value.replace(/\D/g, '').slice(0, 10);
      input.value = d.length < 4 ? d : d.length < 6 ? d.slice(0, 3) + '-' + d.slice(3)
        : d.slice(0, 3) + '-' + d.slice(3, 5) + '-' + d.slice(5);
    });
  }

  window.App = {
    api: api, ApiError: ApiError, loadMe: loadMe, page: page, handleError: handleError,
    toast: toast, showAlert: showAlert, setBusy: setBusy, esc: esc, query: query, pathSegment: pathSegment,
    parseDt: parseDt, toApiDt: toApiDt, toApiDate: toApiDate, addDays: addDays, addMinutes: addMinutes,
    fmtTime: fmtTime, fmtDate: fmtDate, fmtDateTime: fmtDateTime, fmtRange: fmtRange, fmtMinutes: fmtMinutes,
    fmtDays: fmtDays, isOperatingDay: isOperatingDay, atTime: atTime, DAY_CODES: DAY_CODES, DAY_KO: DAY_KO,
    statusClass: statusClass, badge: badge, bindPhoneFormat: bindPhoneFormat, bindBizNumFormat: bindBizNumFormat,
    DB_MESSAGES: DB_MESSAGES
  };
})();
