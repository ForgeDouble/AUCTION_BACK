db = db.getSiblingDB('auction');
db.createUser({
    user: 'auction_user',
    pwd: 'auction_pass',
    roles: [{ role: 'readWrite', db: 'auction' }]
});