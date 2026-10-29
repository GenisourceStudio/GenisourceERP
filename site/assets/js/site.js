/* Genisource MES — 官网交互
   无依赖、渐进增强：所有内容在禁用 JS 时依然可读。 */

(function () {
  "use strict";

  var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------- 1. 顶部状态带无缝循环 ---------- */
  var ticker = (function () {
    var track = document.getElementById("ticker-track");
    if (!track) return null;
    var origin = track.innerHTML;
    var SPEED = 26; // px / s，与设计系统一致

    function build() {
      track.innerHTML = origin;
      var need = Math.max(window.innerWidth * 1.1, 960);
      var guard = 0;
      while (track.scrollWidth < need && guard < 24) {
        track.innerHTML += origin;
        guard++;
      }
      track.innerHTML += track.innerHTML; // 第二份用于 -50% 无缝
      var half = track.scrollWidth / 2;
      track.style.setProperty("--ticker-dur", (half / SPEED).toFixed(1) + "s");
      return half;
    }

    var half = build();
    var timer;
    window.addEventListener("resize", function () {
      clearTimeout(timer);
      timer = setTimeout(function () {
        var need = Math.max(window.innerWidth * 1.1, 960);
        if (!half || half < need) half = build();
      }, 220);
    });
    return track;
  })();

  /* ---------- 2. 滚动入场 ---------- */
  var reveals = document.querySelectorAll(".reveal");
  if (reduced || !("IntersectionObserver" in window)) {
    reveals.forEach(function (el) { el.classList.add("is-in"); });
  } else {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        entry.target.classList.add("is-in");
        io.unobserve(entry.target);
      });
    }, { threshold: 0.08, rootMargin: "0px 0px -6% 0px" });
    reveals.forEach(function (el) { io.observe(el); });
  }

  /* ---------- 3. 顶栏吸顶状态 ---------- */
  var masthead = document.getElementById("masthead");
  if (masthead) {
    var onScrollHead = function () {
      masthead.classList.toggle("is-stuck", window.scrollY > 4);
    };
    onScrollHead();
    window.addEventListener("scroll", onScrollHead, { passive: true });
  }

  /* ---------- 4. 移动端菜单 ---------- */
  var burger = document.getElementById("burger");
  var mobileNav = document.getElementById("mobile-nav");
  if (burger && mobileNav) {
    var setMenu = function (open) {
      burger.setAttribute("aria-expanded", String(open));
      burger.setAttribute("aria-label", open ? "关闭菜单" : "打开菜单");
      mobileNav.classList.toggle("is-open", open);
    };
    burger.addEventListener("click", function () {
      setMenu(burger.getAttribute("aria-expanded") !== "true");
    });
    mobileNav.addEventListener("click", function (e) {
      if (e.target.closest("a")) setMenu(false);
    });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && burger.getAttribute("aria-expanded") === "true") {
        setMenu(false);
        burger.focus();
      }
    });
    window.addEventListener("resize", function () {
      if (window.innerWidth >= 1040) setMenu(false);
    });
  }

  /* ---------- 5. 导航高亮 ---------- */
  var navLinks = Array.prototype.slice.call(document.querySelectorAll(".nav a[href^='#']"));
  if (navLinks.length) {
    var targets = navLinks
      .map(function (a) {
        var el = document.querySelector(a.getAttribute("href"));
        return el ? { link: a, el: el } : null;
      })
      .filter(Boolean);

    var mark = function () {
      var y = window.scrollY + 130;
      var current = null;
      targets.forEach(function (t) {
        if (t.el.offsetTop <= y) current = t;
      });
      targets.forEach(function (t) {
        if (t === current) t.link.setAttribute("aria-current", "true");
        else t.link.removeAttribute("aria-current");
      });
    };
    mark();
    window.addEventListener("scroll", mark, { passive: true });
  }

  /* ---------- 6. 能力切换器 ---------- */
  (function () {
    var list = document.querySelector(".caps__tabs");
    if (!list) return;
    var tabs = Array.prototype.slice.call(list.querySelectorAll("[role='tab']"));

    var select = function (tab, focus) {
      tabs.forEach(function (t) {
        var selected = t === tab;
        t.setAttribute("aria-selected", String(selected));
        t.tabIndex = selected ? 0 : -1;
        var panel = document.getElementById(t.getAttribute("aria-controls"));
        if (panel) panel.hidden = !selected;
      });
      // 懒加载的截图在隐藏面板里不会自动请求，切过去时立刻补上
      var current = document.getElementById(tab.getAttribute("aria-controls"));
      var shot = current && current.querySelector("img");
      if (shot && !shot.complete) {
        shot.loading = "eager";
        shot.setAttribute("loading", "eager");
      }
      if (focus) tab.focus();
      if (tab.scrollIntoView && window.innerWidth < 980) {
        tab.scrollIntoView({ block: "nearest", inline: "center", behavior: reduced ? "auto" : "smooth" });
      }
    };

    // 页面空闲后预取全部章节截图，切换时不再看到空白框
    var warm = function () {
      document.querySelectorAll(".cap-panel img").forEach(function (img) {
        if (!img.complete) img.loading = "eager";
      });
    };
    window.addEventListener("load", function () {
      if (window.requestIdleCallback) window.requestIdleCallback(warm, { timeout: 2500 });
      else setTimeout(warm, 600);
    });

    tabs.forEach(function (tab) {
      tab.addEventListener("click", function () { select(tab, false); });
      tab.addEventListener("keydown", function (e) {
        var i = tabs.indexOf(tab);
        var next = null;
        if (e.key === "ArrowRight" || e.key === "ArrowDown") next = tabs[(i + 1) % tabs.length];
        else if (e.key === "ArrowLeft" || e.key === "ArrowUp") next = tabs[(i - 1 + tabs.length) % tabs.length];
        else if (e.key === "Home") next = tabs[0];
        else if (e.key === "End") next = tabs[tabs.length - 1];
        if (next) {
          e.preventDefault();
          select(next, true);
        }
      });
    });
  })();

  /* ---------- 7. 复制代码 ---------- */
  document.querySelectorAll(".copy-btn").forEach(function (btn) {
    btn.addEventListener("click", function () {
      var target = document.getElementById(btn.getAttribute("data-copy-target"));
      if (!target) return;
      var text = target.textContent.replace(/\s+$/, "");
      var done = function () {
        var label = btn.textContent;
        btn.textContent = "已复制";
        btn.classList.add("is-done");
        setTimeout(function () {
          btn.textContent = label;
          btn.classList.remove("is-done");
        }, 1600);
      };
      if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(done, fallback);
      } else {
        fallback();
      }
      function fallback() {
        var ta = document.createElement("textarea");
        ta.value = text;
        ta.setAttribute("readonly", "");
        ta.style.position = "fixed";
        ta.style.top = "-999px";
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand("copy"); done(); } catch (err) { /* 静默失败 */ }
        document.body.removeChild(ta);
      }
    });
  });

  /* ---------- 8. 年份 ---------- */
  document.querySelectorAll("[data-year]").forEach(function (el) {
    el.textContent = String(new Date().getFullYear());
  });
})();
