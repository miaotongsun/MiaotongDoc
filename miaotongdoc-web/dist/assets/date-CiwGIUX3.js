function o(t){if(!t)return"";const n=new Date(t),e=new Date().getTime()-n.getTime();return e<6e4?"刚刚":e<36e5?`${Math.floor(e/6e4)}分钟前`:e<864e5?`${Math.floor(e/36e5)}小时前`:e<6048e5?`${Math.floor(e/864e5)}天前`:n.toLocaleDateString("zh-CN")}function f(t){return t?new Date(t).toLocaleString("zh-CN"):""}export{f as a,o as f};
//# sourceMappingURL=date-CiwGIUX3.js.map
