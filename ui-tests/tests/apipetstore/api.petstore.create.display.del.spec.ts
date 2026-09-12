import { test, expect } from "@playwright/test"
import { XMLParser, XMLBuilder } from "fast-xml-parser"
import dotenv from 'dotenv';

const payload = {
    "Pet": {
        "id": 12345,
        "Category": {
            "id": 1,
            "name": "BullDog"
        },
        "name": "Pixie",
        "photoUrls": [{
            "photoUrl": "string"
        }],
        "tags": [
            {
                "Tag": {
                    "id": 1,
                    "name": "PixieTheBullDog"
                }
            }
        ],
        "status": "available"
    }
}

const options = {
    format: true
}

const builder = new XMLBuilder(options);
let xmlDataStr = builder.build(payload);

const header = {
    'Accept': 'application/xml',
    'Content-Type': 'application/xml'
}

test.describe('Pet Store Test Suite', () => {
    dotenv.config();

    const xmlParser = new XMLParser();
    let petId = '';
    
    test.use({ ignoreHTTPSErrors: true, });
    
    test.beforeEach('Create a pet object in Pet Store', async ({ request }) => {
        console.log('Request: ',xmlDataStr);
        
        const url = `${process.env.PET_STORE_API_BASE_URL}/v2/pet`;
        let response = await request.post(url, {
            headers: header, data: xmlDataStr
        });
        
        expect(response.status()).toBe(200);

        console.log('Response: ', await response.text());

        const petResponse = xmlParser.parse(await response.text());

        petId = petResponse.Pet.id;

        console.log(petId);
    });

    test('Verify name and availability of the pet in the pet store.', async ({ request }) => {
        const url = `${process.env.PET_STORE_API_BASE_URL}/v2/pet/${petId}`
        console.log(`url: ${url}`);
        
        let response = await request.get(url, {
            headers: header
        });
        
        expect(response.status()).toBe(200);

        console.log('Response: ', await response.text());

        const petResponse = xmlParser.parse(await response.text());

        expect(petResponse.Pet.name).toBe('Pixie');
        expect(petResponse.Pet.status).toBe('available');
    });

    test.afterEach('Delete a pet from pet store', async ({ request }) => {
        const url = `${process.env.PET_STORE_API_BASE_URL}/v2/pet/${petId}`
        console.log(`url: ${url}`);
        
        let response = await request.delete(url, {
            headers: header
        });
        
        expect(response.status()).toBe(200);

        console.log('Response: ', await response.text());

        const petResponse = xmlParser.parse(await response.text());

        expect(petResponse.apiResponse.message).toBe(petId);
    })
});
