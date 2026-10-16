let activeChatId = null;
let activeChatIds = [];
let isCurrentChatGroup = false;
let deferredPrompt;
let currentMessages = [];
let rawChatsMap = new Map(); 

let chatEventSource = null;
let chatEventRetryCount = 0;

let syncEventSource = null;
let syncEventRetryCount = 0;
