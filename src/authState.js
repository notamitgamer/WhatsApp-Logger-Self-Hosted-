const { BufferJSON, initAuthCreds, proto } = require('@whiskeysockets/baileys');

// --- FIRESTORE AUTH ADAPTER FOR BAILEYS ---
async function useFirestoreAuthState(db, collectionName = 'whatsapp_auth') {
    const collection = db.collection(collectionName);

    const writeData = async (data, id) => {
        try {
            const str = JSON.stringify(data, BufferJSON.replacer);
            await collection.doc(id).set({ data: str });
        } catch (err) {
            console.error("System: Error writing auth state:", err.message);
        }
    };

    const readData = async (id, throwOnError = false) => {
        try {
            const doc = await collection.doc(id).get();
            if (doc.exists) {
                return JSON.parse(doc.data().data, BufferJSON.reviver);
            }
        } catch (err) {
            console.error("System: Error reading auth state:", err.message);
            if (throwOnError) throw err;
        }
        return null;
    };

    const removeData = async (id) => {
        try {
            await collection.doc(id).delete();
        } catch (err) {
            console.error("System: Error removing auth state:", err.message);
        }
    };

    let creds;
    try {
        // Pass true to strictly enforce failure up to startWhatsApp for backoff
        creds = (await readData('creds', true)) || initAuthCreds();
    } catch (err) {
        throw err;
    }

    return {
        state: {
            creds,
            keys: {
                get: async (type, ids) => {
                    const data = {};
                    await Promise.all(ids.map(async id => {
                        let value = await readData(`${type}-${id}`);
                        if (type === 'app-state-sync-key' && value) {
                            value = proto.Message.AppStateSyncKeyData.fromObject(value);
                        }
                        data[id] = value;
                    }));
                    return data;
                },
                set: async (data) => {
                    const tasks = [];
                    for (const category in data) {
                        for (const id in data[category]) {
                            const value = data[category][id];
                            const docId = `${category}-${id}`;
                            if (value) {
                                tasks.push(writeData(value, docId));
                            } else {
                                tasks.push(removeData(docId));
                            }
                        }
                    }
                    await Promise.all(tasks);
                }
            }
        },
        saveCreds: () => {
            return writeData(creds, 'creds');
        },
        clearState: async () => {
            await removeData('creds');
        }
    };
}

module.exports = { useFirestoreAuthState };
